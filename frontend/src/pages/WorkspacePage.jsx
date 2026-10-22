import { roleLabel, useAuth } from '../auth.jsx'

export default function WorkspacePage() {
  const { user, signOut } = useAuth()

  return (
    <div className="shell">
      <header className="topbar">
        <p className="brand">Research Assistant Management</p>
        <div className="who">
          <span>{user.name}</span>
          <span className="role">{roleLabel(user.role)}</span>
          <button type="button" onClick={signOut}>Sign out</button>
        </div>
      </header>
      <main className="page">
        <p className="eyebrow">Signed in</p>
        <h1>Welcome, {user.name}</h1>
        <p className="lede">
          You are signed in as a {roleLabel(user.role).toLowerCase()}. Projects, tasks, and weekly reports come next.
        </p>
      </main>
    </div>
  )
}
