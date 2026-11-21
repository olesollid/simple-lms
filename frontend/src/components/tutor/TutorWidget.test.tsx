import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { Link, MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { TutorStreamError } from '../../services/aiTutor'
import type { TutorStreamEvent } from '../../types/aiTutor'
import TutorWidget from './TutorWidget'

const getConfig = vi.hoisted(() => vi.fn())
const streamChat = vi.hoisted(() => vi.fn())
const getCurrentUser = vi.hoisted(() => vi.fn())
const getCourse = vi.hoisted(() => vi.fn())
const getContent = vi.hoisted(() => vi.fn())
vi.mock('../../services', () => ({
  aiTutor: { getConfig, streamChat },
  users: { getCurrentUser },
  courses: { getCourse },
  contents: { getContent },
}))

const courses: Record<number, { id: number; title: string }> = {
  1: { id: 1, title: 'DAT250' },
  2: { id: 2, title: 'INF100' },
}

function reply(...texts: string[]) {
  streamChat.mockImplementation(async function* (): AsyncGenerator<TutorStreamEvent> {
    for (const text of texts) yield { type: 'delta', text }
    yield { type: 'done' }
  })
}

/** A stream that fails as soon as it is read, like a refused or broken request. */
function failing(error: unknown) {
  return () => ({
    [Symbol.asyncIterator]: () => ({ next: () => Promise.reject(error) }),
  })
}

function Harness({ path = '/courses' }: { path?: string }) {
  return (
    <MemoryRouter initialEntries={[path]}>
      <nav>
        <Link to="/courses">dashboard</Link>
        <Link to="/courses/1">course one</Link>
        <Link to="/courses/2">course two</Link>
        <Link to="/courses/1/modules/2/contents/7">lab three</Link>
        <Link to="/courses/1/modules/2/contents/8">syllabus</Link>
        <Link to="/profile">profile</Link>
      </nav>
      <TutorWidget />
    </MemoryRouter>
  )
}

async function openPanel(user = userEvent.setup()) {
  await user.click(await screen.findByRole('button', { name: /AI tutor/ }))
  return user
}

async function ask(user: ReturnType<typeof userEvent.setup>, text: string) {
  await user.type(screen.getByLabelText('Message to the AI tutor'), text)
  await user.click(screen.getByRole('button', { name: 'Send' }))
}

describe('TutorWidget', () => {
  beforeEach(() => {
    sessionStorage.clear()
    getConfig.mockReset()
    getConfig.mockResolvedValue({ enabled: true })
    streamChat.mockReset()
    reply('Sure, let us think about it.')
    getCurrentUser.mockReset()
    getCurrentUser.mockResolvedValue({ email: 'student@example.com', name: 'Student' })
    getCourse.mockReset()
    getCourse.mockImplementation((id: number) => Promise.resolve(courses[id]))
    getContent.mockReset()
    getContent.mockImplementation((id: number) => Promise.resolve(id === 7
      ? { id: 7, type: 'Assignment', title: 'Lab 3' }
      : { id: 8, type: 'StaticPage', title: 'Syllabus' }))
  })

  afterEach(() => {
    delete (window as { matchMedia?: unknown }).matchMedia
  })

  describe('feature flag', () => {
    it('shows nothing when the backend says the tutor is off', async () => {
      getConfig.mockResolvedValue({ enabled: false })
      render(<Harness />)

      await waitFor(() => expect(getConfig).toHaveBeenCalled())
      expect(screen.queryByRole('button', { name: /AI tutor/ })).not.toBeInTheDocument()
      expect(getCurrentUser).not.toHaveBeenCalled()
    })

    it('shows nothing when the config request fails', async () => {
      getConfig.mockRejectedValue(new Error('network'))
      render(<Harness />)

      await waitFor(() => expect(getConfig).toHaveBeenCalled())
      expect(screen.queryByRole('button', { name: /AI tutor/ })).not.toBeInTheDocument()
    })

    it('asks for the per-course flag on a course page', async () => {
      render(<Harness path="/courses/2" />)

      await screen.findByRole('button', { name: /AI tutor/ })
      expect(getConfig).toHaveBeenCalledWith(2, expect.anything())
    })

    it('hides the widget on a course where the tutor is off and brings it back elsewhere', async () => {
      const user = userEvent.setup()
      getConfig.mockImplementation((courseId: number | null) =>
        Promise.resolve({ enabled: courseId !== 2 }))
      render(<Harness path="/courses/1" />)
      await screen.findByRole('button', { name: /AI tutor/ })

      await user.click(screen.getByRole('link', { name: 'course two' }))
      await waitFor(() => expect(screen.queryByRole('button', { name: /AI tutor/ })).not.toBeInTheDocument())

      await user.click(screen.getByRole('link', { name: 'course one' }))
      expect(await screen.findByRole('button', { name: /AI tutor/ })).toBeInTheDocument()
    })
  })

  describe('opening and closing', () => {
    it('opens a dialog, moves focus to the message box, and tells students it is an AI', async () => {
      render(<Harness />)
      await openPanel()

      const dialog = screen.getByRole('dialog', { name: 'AI tutor' })
      expect(dialog).toBeInTheDocument()
      expect(screen.getByLabelText('Message to the AI tutor')).toHaveFocus()
      expect(dialog).toHaveTextContent(/chatting with an AI, not a person/i)
      expect(screen.getByRole('log', { name: 'Conversation' })).toHaveAttribute('aria-live', 'polite')
    })

    it('closes with the close button and returns focus to the launcher', async () => {
      render(<Harness />)
      const user = await openPanel()

      await user.click(screen.getByRole('button', { name: 'Close AI tutor' }))

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: /AI tutor/ })).toHaveFocus()
    })

    it('closes with Escape and returns focus to the launcher', async () => {
      render(<Harness />)
      const user = await openPanel()

      await user.keyboard('{Escape}')

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: /AI tutor/ })).toHaveFocus()
    })

    it('exposes the launcher state to assistive technology', async () => {
      render(<Harness />)
      const launcher = await screen.findByRole('button', { name: /AI tutor/ })

      expect(launcher).toHaveAttribute('aria-expanded', 'false')
      expect(launcher).toHaveAttribute('aria-controls', 'tutor-panel')
    })

    it('is a modal dialog on narrow screens', async () => {
      window.matchMedia = vi.fn().mockReturnValue({
        matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn(),
      }) as unknown as typeof window.matchMedia
      render(<Harness />)
      await openPanel()

      expect(screen.getByRole('dialog')).toHaveAttribute('aria-modal', 'true')
    })
  })

  describe('persistence', () => {
    it('stays open with the conversation intact while navigating', async () => {
      render(<Harness path="/courses/1" />)
      const user = await openPanel()
      await ask(user, 'What is JPA?')
      await screen.findByText('Sure, let us think about it.')

      await user.click(screen.getByRole('link', { name: 'profile' }))

      expect(screen.getByRole('dialog', { name: 'AI tutor' })).toBeInTheDocument()
      expect(screen.getByText('What is JPA?')).toBeInTheDocument()
      expect(screen.getByText('Sure, let us think about it.')).toBeInTheDocument()
    })

    it('restores the open panel and conversation in the same session after a reload', async () => {
      const first = render(<Harness />)
      const user = await openPanel()
      await ask(user, 'Remember me')
      await screen.findByText('Sure, let us think about it.')
      first.unmount()

      render(<Harness />)

      expect(await screen.findByRole('dialog', { name: 'AI tutor' })).toBeInTheDocument()
      expect(screen.getByText('Remember me')).toBeInTheDocument()
      expect(screen.getByRole('dialog')).not.toContainElement(document.activeElement as HTMLElement | null)
    })

    it('clears the conversation with "New conversation"', async () => {
      render(<Harness />)
      const user = await openPanel()
      await ask(user, 'First question')
      await screen.findByText('Sure, let us think about it.')

      await user.click(screen.getByRole('button', { name: 'New conversation' }))

      expect(screen.queryByText('First question')).not.toBeInTheDocument()
      expect(screen.queryByText('Sure, let us think about it.')).not.toBeInTheDocument()
      expect(screen.getByText(/study assistant/)).toBeInTheDocument()
    })
  })

  describe('context-aware greetings', () => {
    it('greets generically on the dashboard', async () => {
      render(<Harness path="/courses" />)
      await openPanel()

      expect(await screen.findByText(/I'm your AI study assistant/)).toBeInTheDocument()
    })

    it('names the course on a course page', async () => {
      render(<Harness path="/courses/1" />)
      await openPanel()

      expect(await screen.findByText("I see you're working on DAT250. How can I help?")).toBeInTheDocument()
    })

    it('names the assignment and its course on an assignment page', async () => {
      render(<Harness path="/courses/1/modules/2/contents/7" />)
      await openPanel()

      expect(await screen.findByText(
        'I see you\'re working on the assignment "Lab 3" in DAT250. How can I help?',
      )).toBeInTheDocument()
    })

    it('treats non-assignment content as a course page', async () => {
      render(<Harness path="/courses/1/modules/2/contents/8" />)
      await openPanel()

      expect(await screen.findByText("I see you're working on DAT250. How can I help?")).toBeInTheDocument()
    })

    it('greets generically on other pages', async () => {
      render(<Harness path="/profile" />)
      await openPanel()

      expect(await screen.findByText(/I'm your AI study assistant/)).toBeInTheDocument()
    })

    it('updates the greeting when the student moves before chatting', async () => {
      render(<Harness path="/courses/1" />)
      const user = await openPanel()
      await screen.findByText("I see you're working on DAT250. How can I help?")

      await user.click(screen.getByRole('link', { name: 'course two' }))

      expect(await screen.findByText("I see you're working on INF100. How can I help?")).toBeInTheDocument()
      expect(screen.queryByText(/working on DAT250/)).not.toBeInTheDocument()
    })

    it('adds a notice and uses the new context after switching courses mid-conversation', async () => {
      render(<Harness path="/courses/1" />)
      const user = await openPanel()
      await ask(user, 'First question')
      await screen.findByText('Sure, let us think about it.')

      await user.click(screen.getByRole('link', { name: 'course two' }))
      expect(await screen.findByText("You're now in INF100")).toBeInTheDocument()

      await ask(user, 'Second question')
      await waitFor(() => expect(streamChat).toHaveBeenCalledTimes(2))
      expect(streamChat.mock.calls[1][0].context).toEqual(
        { page_type: 'course', course_id: 2, assignment_id: null },
      )
    })

    it('sends the assignment id on an assignment page', async () => {
      render(<Harness path="/courses/1/modules/2/contents/7" />)
      const user = await openPanel()
      await screen.findByText(/Lab 3/)

      await ask(user, 'Help')

      await waitFor(() => expect(streamChat).toHaveBeenCalled())
      expect(streamChat.mock.calls[0][0].context).toEqual(
        { page_type: 'assignment', course_id: 1, assignment_id: 7 },
      )
    })

    it('shows an error with retry when course details cannot be loaded, and recovers', async () => {
      getCourse.mockRejectedValueOnce(new Error('LMS down'))
      render(<Harness path="/courses/1" />)
      const user = await openPanel()

      expect(await screen.findByRole('alert')).toHaveTextContent(/couldn't load details/i)
      expect(screen.getByRole('log')).toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'Retry' }))

      expect(await screen.findByText("I see you're working on DAT250. How can I help?")).toBeInTheDocument()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })
  })

  describe('messages', () => {
    it('sends the page context, timezone and history but not local greetings', async () => {
      render(<Harness path="/courses/1" />)
      const user = await openPanel()
      await screen.findByText(/working on DAT250/)

      await ask(user, 'Hello')
      await screen.findByText('Sure, let us think about it.')
      await ask(user, 'And then?')

      await waitFor(() => expect(streamChat).toHaveBeenCalledTimes(2))
      const [request, options] = streamChat.mock.calls[1]
      expect(request.messages).toEqual([
        { role: 'user', content: 'Hello' },
        { role: 'assistant', content: 'Sure, let us think about it.' },
        { role: 'user', content: 'And then?' },
      ])
      expect(request.context).toEqual({ page_type: 'course', course_id: 1, assignment_id: null })
      expect(request.timezone).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone)
      expect(options.signal).toBeInstanceOf(AbortSignal)
    })

    it('shows a typing indicator until the first text arrives, then streams in', async () => {
      let release: () => void = () => undefined
      streamChat.mockImplementation(async function* (): AsyncGenerator<TutorStreamEvent> {
        await new Promise<void>(resolve => { release = resolve })
        yield { type: 'delta', text: 'Almost ' }
        yield { type: 'delta', text: 'there' }
        yield { type: 'done' }
      })
      render(<Harness />)
      const user = await openPanel()

      await ask(user, 'Hi')

      expect(await screen.findByText('AI tutor is typing')).toBeInTheDocument()
      expect(screen.getByRole('log')).toHaveAttribute('aria-busy', 'true')
      expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled()

      release()

      expect(await screen.findByText('Almost there')).toBeInTheDocument()
      expect(screen.queryByText('AI tutor is typing')).not.toBeInTheDocument()
      expect(screen.getByRole('log')).toHaveAttribute('aria-busy', 'false')
    })

    it('sends with Enter and keeps a newline for Shift+Enter', async () => {
      render(<Harness />)
      const user = await openPanel()
      const input = screen.getByLabelText('Message to the AI tutor')

      await user.type(input, 'line one{Shift>}{Enter}{/Shift}line two{Enter}')

      await waitFor(() => expect(streamChat).toHaveBeenCalledTimes(1))
      expect(streamChat.mock.calls[0][0].messages[0].content).toBe('line one\nline two')
    })

    it('does not send an empty message', async () => {
      render(<Harness />)
      await openPanel()

      expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled()
      expect(streamChat).not.toHaveBeenCalled()
    })

    it('renders Markdown, with links, no images and no unsafe URLs', async () => {
      reply('**Bold idea** with [the lab](/courses/1/modules/2/contents/7), ',
        '[docs](https://example.com/x), ![pixel](https://evil.example/p.png) ',
        'and [bad](javascript:alert(1)).\n\n# Big heading\n\n- first\n- second')
      render(<Harness />)
      const user = await openPanel()

      await ask(user, 'Explain')

      expect(await screen.findByText('Bold idea')).toContainHTML('Bold idea')
      expect(screen.getByText('Bold idea').tagName).toBe('STRONG')
      expect(screen.getByRole('link', { name: 'the lab' })).toHaveAttribute('href', '/courses/1/modules/2/contents/7')
      const external = screen.getByRole('link', { name: 'docs' })
      expect(external).toHaveAttribute('target', '_blank')
      expect(external).toHaveAttribute('rel', 'noopener noreferrer')
      expect(document.querySelector('img')).toBeNull()
      expect(screen.queryByRole('link', { name: 'bad' })).not.toBeInTheDocument()
      expect(screen.getAllByRole('listitem')).toHaveLength(2)
      expect(screen.queryByRole('heading', { name: 'Big heading' })).not.toBeInTheDocument()
    })
  })

  describe('failures', () => {
    it('shows an error with Retry, and retrying resends the same question', async () => {
      streamChat.mockImplementationOnce(failing(new TutorStreamError('provider down', 503)))
      render(<Harness />)
      const user = await openPanel()

      await ask(user, 'Will this work?')

      expect(await screen.findByRole('alert')).toHaveTextContent(/unavailable right now/i)
      expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled()
      reply('It works now.')

      await user.click(screen.getByRole('button', { name: 'Retry' }))

      expect(await screen.findByText('It works now.')).toBeInTheDocument()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      expect(streamChat.mock.calls[1][0].messages).toEqual([{ role: 'user', content: 'Will this work?' }])
      expect(screen.getAllByText('Will this work?')).toHaveLength(1)
    })

    it('tells the student how long to wait when rate limited', async () => {
      streamChat.mockImplementation(failing(new TutorStreamError('Too many requests', 429, null, 30)))
      render(<Harness />)
      const user = await openPanel()

      await ask(user, 'Again')

      expect(await screen.findByRole('alert')).toHaveTextContent('wait about 30 seconds')
    })

    it('explains a refused request for a course the student cannot use', async () => {
      streamChat.mockImplementation(failing(new TutorStreamError('Forbidden', 403)))
      render(<Harness />)
      const user = await openPanel()

      await ask(user, 'Hi')

      expect(await screen.findByRole('alert')).toHaveTextContent(/isn't available for this course/i)
    })

    it('keeps the partial answer and offers Retry when the stream breaks midway', async () => {
      streamChat.mockImplementationOnce(async function* (): AsyncGenerator<TutorStreamEvent> {
        yield { type: 'delta', text: 'Half an answ' }
        yield { type: 'error', message: 'provider reset' }
      })
      render(<Harness />)
      const user = await openPanel()

      await ask(user, 'Hi')

      expect(await screen.findByText('Half an answ')).toBeInTheDocument()
      expect(screen.getByRole('alert')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
    })

    it('reports a network failure without breaking the page around it', async () => {
      streamChat.mockImplementation(failing(new TypeError('Failed to fetch')))
      render(<Harness />)
      const user = await openPanel()

      await ask(user, 'Hi')

      expect(await screen.findByRole('alert')).toHaveTextContent(/check your connection/i)
      expect(screen.getByRole('link', { name: 'profile' })).toBeInTheDocument()
    })
  })

  describe('accessibility', () => {
    it('has no critical or serious axe violations with the panel open', async () => {
      streamChat.mockImplementationOnce(failing(new TutorStreamError('x', 503)))
      render(<Harness path="/courses/1" />)
      const user = await openPanel()
      await ask(user, 'Hello')
      await screen.findByRole('alert')

      // jsdom does not compute layout or real colors, so color contrast is checked in a browser.
      const results = await axe.run(document.body, { rules: { 'color-contrast': { enabled: false } } })
      const serious = results.violations.filter(v => v.impact === 'critical' || v.impact === 'serious')

      expect(serious.map(v => `${v.id}: ${v.help}`)).toEqual([])
    })

    it('has no critical or serious axe violations with the panel closed', async () => {
      render(<Harness />)
      await screen.findByRole('button', { name: /AI tutor/ })

      const results = await axe.run(document.body, { rules: { 'color-contrast': { enabled: false } } })
      const serious = results.violations.filter(v => v.impact === 'critical' || v.impact === 'serious')

      expect(serious.map(v => `${v.id}: ${v.help}`)).toEqual([])
    })
  })
})
