import { Link } from 'react-router-dom'
import { roleLabel, useAuth } from './auth.jsx'

export default function Shell({ children }) {
  const { user, signOut } = useAuth()

  return (
    <div className="shell">
      <header className="topbar">
        <Link className="brand" to="/">Research Assistant Management</Link>
        <div className="who">
          <Link to="/">Projects</Link>
          <span>{user.name}</span>
          <span className="role">{roleLabel(user.role)}</span>
          <button type="button" onClick={signOut}>Sign out</button>
        </div>
      </header>
      <main className="page wide">{children}</main>
    </div>
  )
}
