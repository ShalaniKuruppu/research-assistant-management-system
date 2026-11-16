import { useEffect, useState } from 'react'
import { ApiError, api, apiFormData } from '../api.js'
import { roleLabel, useAuth } from '../auth.jsx'
import Shell from '../Shell.jsx'

export default function ProfilePage() {
  const { user, refreshUser } = useAuth()
  const [email, setEmail] = useState(user.email || '')
  const [phone, setPhone] = useState(user.phone || '')
  const [preview, setPreview] = useState(user.profileImageUrl || '')
  const [file, setFile] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [pending, setPending] = useState(false)

  useEffect(() => {
    setEmail(user.email || '')
    setPhone(user.phone || '')
    if (!file) {
      setPreview(user.profileImageUrl || '')
    }
  }, [user, file])

  useEffect(() => () => {
    if (preview && preview.startsWith('blob:')) {
      URL.revokeObjectURL(preview)
    }
  }, [preview])

  async function onSubmit(event) {
    event.preventDefault()
    setError('')
    setNotice('')
    setPending(true)
    try {
      await api('/api/me', {
        method: 'PATCH',
        body: {
          email: email.trim(),
          phone: phone.trim(),
        },
      })
      if (file) {
        const body = new FormData()
        body.append('file', file)
        await apiFormData('/api/me/profile-picture', body)
        setFile(null)
      }
      await refreshUser()
      setNotice('Profile updated.')
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not update the profile')
    } finally {
      setPending(false)
    }
  }

  async function onRemovePicture() {
    setError('')
    setNotice('')
    setPending(true)
    try {
      await api('/api/me', {
        method: 'PATCH',
        body: { clearProfileImage: true },
      })
      if (preview && preview.startsWith('blob:')) {
        URL.revokeObjectURL(preview)
      }
      setFile(null)
      setPreview('')
      await refreshUser()
      setNotice('Profile picture removed.')
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not remove the profile picture')
    } finally {
      setPending(false)
    }
  }

  function onPickFile(event) {
    const next = event.target.files?.[0] || null
    if (preview && preview.startsWith('blob:')) {
      URL.revokeObjectURL(preview)
    }
    setFile(next)
    setPreview(next ? URL.createObjectURL(next) : (user.profileImageUrl || ''))
  }

  return (
    <Shell>
      <div className="page-head">
        <div>
          <p className="eyebrow">Account</p>
          <h1>Your profile</h1>
          <p className="lede">Update your email, contact number, and profile picture.</p>
        </div>
      </div>
      <form className="card form-card profile-form" onSubmit={onSubmit}>
        {error ? <p className="form-error" role="alert">{error}</p> : null}
        {notice ? <p className="form-note" role="status">{notice}</p> : null}

        <div className="profile-picture-row">
          {preview ? (
            <img className="assistant-avatar" src={preview} alt="" width={72} height={72} />
          ) : (
            <div className="assistant-avatar fallback" aria-hidden="true">
              {initialsFor(user.name)}
            </div>
          )}
          <div className="stack">
            <label>
              Profile picture
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif"
                onChange={onPickFile}
              />
            </label>
            <p className="meta">JPEG, PNG, WEBP, or GIF up to 2 MB.</p>
            {(preview || user.profileImageUrl) ? (
              <button type="button" onClick={onRemovePicture} disabled={pending}>
                Remove picture
              </button>
            ) : null}
          </div>
        </div>

        <label>
          Name
          <input value={user.name} disabled />
        </label>
        <label>
          Role
          <input value={roleLabel(user.role)} disabled />
        </label>
        <label>
          Email
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
            maxLength={254}
          />
        </label>
        <label>
          Contact number
          <input
            type="tel"
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
            maxLength={40}
            placeholder="Optional"
          />
        </label>
        <button className="primary" type="submit" disabled={pending}>
          {pending ? 'Saving…' : 'Save profile'}
        </button>
      </form>
    </Shell>
  )
}

function initialsFor(name) {
  const parts = String(name || '')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase()
}
