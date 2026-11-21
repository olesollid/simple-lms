import { beforeEach, describe, expect, it } from 'vitest'
import { clearAllTutorStorage, loadTutorState, saveTutorState } from './tutorStorage'

describe('tutorStorage', () => {
  beforeEach(() => sessionStorage.clear())

  it('round-trips the conversation per user', () => {
    saveTutorState('a@example.com', {
      open: true,
      entries: [{ id: '1', kind: 'user', content: 'Hi', status: 'done' }],
      noticedCourseId: 3,
    })

    expect(loadTutorState('a@example.com')).toMatchObject({ open: true, noticedCourseId: 3 })
    expect(loadTutorState('a@example.com')?.entries).toHaveLength(1)
    expect(loadTutorState('b@example.com')).toBeNull()
  })

  it('marks an answer that was streaming when the page went away as interrupted', () => {
    saveTutorState('a@example.com', {
      open: false,
      entries: [{ id: '1', kind: 'assistant', content: 'Part', status: 'streaming' }],
      noticedCourseId: null,
    })

    expect(loadTutorState('a@example.com')?.entries[0]).toMatchObject({ status: 'error' })
  })

  it('ignores corrupt data', () => {
    sessionStorage.setItem('ai-tutor:v1:a@example.com', '{nope')

    expect(loadTutorState('a@example.com')).toBeNull()
  })

  it('clears every stored conversation but leaves other keys alone', () => {
    saveTutorState('a@example.com', { open: false, entries: [], noticedCourseId: null })
    saveTutorState('b@example.com', { open: false, entries: [], noticedCourseId: null })
    sessionStorage.setItem('unrelated', 'keep')

    clearAllTutorStorage()

    expect(loadTutorState('a@example.com')).toBeNull()
    expect(loadTutorState('b@example.com')).toBeNull()
    expect(sessionStorage.getItem('unrelated')).toBe('keep')
  })
})
