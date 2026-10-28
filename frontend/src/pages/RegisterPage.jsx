import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../api.js'
import { useAuth } from '../auth.jsx'

export default function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState('SUPERVISOR')
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)

  async function onSubmit(event) {
    event.preventDefault()
    setError('')
    if (password.length < 8) {
      setError('Password must be at least 8 characters')
      return
    }
    setPending(true)
    try {
      await register({
        name: name.trim(),
        email: email.trim(),
        password,
        role,
      })
      navigate('/', { replace: true })
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not create the account')
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="gate">
      <section className="gate-copy">
        <p className="eyebrow">Research Assistant Management</p>
        <h1>Create an account</h1>
        <p>Choose a role now.</p>
      </section>
      <form className="card" onSubmit={onSubmit}>
        <h2>Register</h2>
        {error ? <p className="form-error" role="alert">{error}</p> : null}
        <label>
          Name
          <input
            value={name}
            onChange={(event) => setName(event.target.value)}
            autoComplete="name"
            required
            maxLength={150}
          />
        </label>
        <label>
          Email
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="email"
            required
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="new-password"
            minLength={8}
            required
          />
        </label>
        <fieldset>
          <legend>Role</legend>
          <label className="choice">
            <input
              type="radio"
              name="role"
              value="SUPERVISOR"
              checked={role === 'SUPERVISOR'}
              onChange={() => setRole('SUPERVISOR')}
            />
            Supervisor
          </label>
          <label className="choice">
            <input
              type="radio"
              name="role"
              value="RESEARCH_ASSISTANT"
              checked={role === 'RESEARCH_ASSISTANT'}
              onChange={() => setRole('RESEARCH_ASSISTANT')}
            />
            Research assistant
          </label>
        </fieldset>
        <button className="primary" type="submit" disabled={pending}>
          {pending ? 'Creating account…' : 'Create account'}
        </button>
        <p className="form-note">
          Already registered? <Link to="/sign-in">Sign in</Link>
        </p>
      </form>
    </div>
  )
}
