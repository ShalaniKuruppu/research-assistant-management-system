import { createContext, useContext, useEffect, useState } from 'react'
import { api, getToken, setToken } from './api.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    if (!getToken()) {
      setReady(true)
      return
    }
    api('/api/me')
      .then(setUser)
      .catch(() => {
        setToken(null)
        setUser(null)
      })
      .finally(() => setReady(true))
  }, [])

  async function signIn(email, password) {
    const result = await api('/api/auth/login', {
      method: 'POST',
      body: { email, password },
    })
    setToken(result.token)
    setUser(result.user)
  }

  async function register(account) {
    const result = await api('/api/auth/register', {
      method: 'POST',
      body: account,
    })
    setToken(result.token)
    setUser(result.user)
  }

  function signOut() {
    setToken(null)
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, ready, signIn, register, signOut }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return value
}

export function roleLabel(role) {
  return role === 'SUPERVISOR' ? 'Supervisor' : 'Research assistant'
}
