import Input from './ui/Input'

export default function SubmissionPolicyFields({ deadline, acceptsLate, onDeadline, onAcceptsLate }: {
  deadline: string
  acceptsLate: boolean
  onDeadline: (value: string) => void
  onAcceptsLate: (value: boolean) => void
}) {
  return <>
    <Input label="Deadline (Europe/Oslo)" type="datetime-local" step="1"
      value={deadline} onChange={event => onDeadline(event.target.value)} />
    <p className="page-description">Optional. Times use Europe/Oslo regardless of your browser time zone.
      Submitting exactly at the deadline is on time.</p>
    <label className="ui-checkbox-field">
      <input type="checkbox" checked={acceptsLate} onChange={event => onAcceptsLate(event.target.checked)} />
      Accept late submissions
    </label>
  </>
}
