import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../api.js'
import { useAuth } from '../auth.jsx'

export default function SignInPage() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)

  async function onSubmit(event) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const email = String(form.get('email') || '').trim()
    const password = String(form.get('password') || '')
    setError('')
    setPending(true)
    try {
      await signIn(email, password)
      navigate('/', { replace: true })
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Sign-in failed. Is the API running on port 8080?')
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="gate">
      <section className="gate-copy">
        <p className="eyebrow">Research Assistant Management</p>
        <h1>Sign in to your workspace</h1>
        <p>Supervisors run projects. Research assistants update tasks and file the weekly report.</p>
      </section>
      <form className="card" onSubmit={onSubmit}>
        <h2>Sign in</h2>
        {error ? <p className="form-error" role="alert">{error}</p> : null}
        <label>
          Email
          <input
            name="email"
            type="email"
            autoComplete="username"
            required
          />
        </label>
        <label>
          Password
          <input
            name="password"
            type="password"
            autoComplete="current-password"
            required
          />
        </label>
        <button className="primary" type="submit" disabled={pending}>
          {pending ? 'Signing in…' : 'Sign in'}
        </button>
        <p className="form-note">
          No account yet? <Link to="/register">Create one</Link>
        </p>
      </form>
    </div>
  )
}
