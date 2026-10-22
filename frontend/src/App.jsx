import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth.jsx'
import RegisterPage from './pages/RegisterPage.jsx'
import SignInPage from './pages/SignInPage.jsx'
import WorkspacePage from './pages/WorkspacePage.jsx'

function RequireAuth({ children }) {
  const { user, ready } = useAuth()
  if (!ready) {
    return <p className="status-line">Checking your session…</p>
  }
  if (!user) {
    return <Navigate to="/sign-in" replace />
  }
  return children
}

function GuestOnly({ children }) {
  const { user, ready } = useAuth()
  if (!ready) {
    return <p className="status-line">Checking your session…</p>
  }
  if (user) {
    return <Navigate to="/" replace />
  }
  return children
}

export default function App() {
  return (
    <Routes>
      <Route
        path="/sign-in"
        element={
          <GuestOnly>
            <SignInPage />
          </GuestOnly>
        }
      />
      <Route
        path="/register"
        element={
          <GuestOnly>
            <RegisterPage />
          </GuestOnly>
        }
      />
      <Route
        path="/"
        element={
          <RequireAuth>
            <WorkspacePage />
          </RequireAuth>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
