import CoursesPage from './pages/CoursesPage'
import CourseDetailPage from './pages/CourseDetailPage'
import { Navigate, Route, Routes } from 'react-router'
import AppLayout from './components/AppLayout'
import PlaceholderPage from './pages/PlaceholderPage'
import './App.css'

function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<PlaceholderPage title="Dashboard" description="Your learning, all in one place." message="Your course activity and upcoming deadlines will appear here." />} />
        <Route path="courses" element={<CoursesPage />} />
        <Route path="courses/:id" element={<CourseDetailPage />} />
        <Route path="profile" element={<PlaceholderPage title="Profile" description="Your personal learning space." message="Your account details and preferences will appear here." />} />
        <Route path="*" element={<PlaceholderPage title="Page not found" description="We couldn’t find that page." message="Use the navigation to return to your learning space." />} />
      </Route>
    </Routes>
  )
}

export default App
