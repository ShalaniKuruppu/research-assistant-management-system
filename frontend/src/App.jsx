import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth.jsx'
import AssistantProjectPage from './pages/AssistantProjectPage.jsx'
import NewProjectPage from './pages/NewProjectPage.jsx'
import ProjectPage from './pages/ProjectPage.jsx'
import ProjectsPage from './pages/ProjectsPage.jsx'
import RegisterPage from './pages/RegisterPage.jsx'
import SignInPage from './pages/SignInPage.jsx'

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

function ProjectRoute() {
  const { user } = useAuth()
  if (user.role === 'RESEARCH_ASSISTANT') {
    return <AssistantProjectPage />
  }
  return <ProjectPage />
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
      <Route path="/" element={<RequireAuth><ProjectsPage /></RequireAuth>} />
      <Route path="/projects/new" element={<RequireAuth><NewProjectPage /></RequireAuth>} />
      <Route path="/projects/:projectId" element={<RequireAuth><ProjectRoute /></RequireAuth>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
