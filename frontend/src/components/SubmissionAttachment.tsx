import { API_BASE_URL } from '../services/api'
import type { SubmissionResponse } from '../types/submission'

export default function SubmissionAttachment({ submission }: { submission: SubmissionResponse }) {
  if (!submission.fileName) return null
  return <p><a className="text-link" href={`${API_BASE_URL.replace(/\/+$/, '')}/submissions/${submission.id}/file`}>
    Download {submission.fileName}
  </a> {submission.fileSize != null && `(${Math.ceil(submission.fileSize / 1024)} KB)`}</p>
}
