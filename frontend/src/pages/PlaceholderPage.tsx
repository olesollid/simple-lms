import { Link } from 'react-router'

export default function PlaceholderPage({ title, description, message }: { title: string; description: string; message: string }) {
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">Your workspace</p>
          <h1>{title}</h1>
          <p className="page-description">{description}</p>
        </div>
      </div>
      <section className="placeholder-panel" aria-label={`${title} overview`}>
        <span className="eyebrow">Coming soon</span>
        <h2>A space for your next chapter</h2>
        <p>{message}</p>
        <Link className="text-link" to="/courses">Explore courses →</Link>
      </section>
    </>
  )
}
