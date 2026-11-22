import CoursesPage from './pages/CoursesPage'
import CourseDetailPage from './pages/CourseDetailPage'
import ContentDetailPage from './pages/ContentDetailPage'
import StudentDeliveriesPage from './pages/StudentDeliveriesPage'
import ProfilePage from './pages/ProfilePage'
import { Navigate, Route, Routes } from 'react-router'
import AppLayout from './components/AppLayout'
import PlaceholderPage from './pages/PlaceholderPage'
import './App.css'

function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<Navigate to="/courses" replace />} />
        <Route path="dashboard" element={<Navigate to="/courses" replace />} />
        <Route path="courses" element={<CoursesPage />} />
        <Route path="courses/:id" element={<CourseDetailPage />} />
        <Route path="courses/:courseId/students" element={<StudentDeliveriesPage />} />
        <Route path="courses/:courseId/modules/:moduleId/contents/:contentId" element={<ContentDetailPage />} />
        <Route path="profile" element={<ProfilePage />} />
        <Route path="*" element={<PlaceholderPage title="Page not found" description="We couldn’t find that page." message="Use the navigation to return to your learning space." />} />
      </Route>
    </Routes>
  )
}

export default App
