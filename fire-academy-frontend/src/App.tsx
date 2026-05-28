import { lazy, Suspense } from 'react'
import { Routes, Route } from 'react-router-dom'
import { Layout } from './components/layout/Layout'
import { HomePage } from './pages/HomePage'
import { ProtectedRoute } from './components/layout/ProtectedRoute'
import { AdminRoute } from './components/layout/AdminRoute'
import { ErrorBoundary } from './components/ui/ErrorBoundary'
import { ScrollToTop } from './components/ScrollToTop'
import { LoadingSpinner } from './components/ui/LoadingSpinner'

const LoginPage = lazy(() => import('./pages/LoginPage').then(m => ({ default: m.LoginPage })))
const RegisterPage = lazy(() => import('./pages/RegisterPage').then(m => ({ default: m.RegisterPage })))
const VerifyEmailPage = lazy(() => import('./pages/VerifyEmailPage').then(m => ({ default: m.VerifyEmailPage })))
const ForgotPasswordPage = lazy(() => import('./pages/ForgotPasswordPage').then(m => ({ default: m.ForgotPasswordPage })))
const ResetPasswordPage = lazy(() => import('./pages/ResetPasswordPage').then(m => ({ default: m.ResetPasswordPage })))
const ResendVerificationPage = lazy(() => import('./pages/ResendVerificationPage').then(m => ({ default: m.ResendVerificationPage })))
const OAuthCallbackPage = lazy(() => import('./pages/OAuthCallbackPage').then(m => ({ default: m.OAuthCallbackPage })))
const SettingsPage = lazy(() => import('./pages/SettingsPage').then(m => ({ default: m.SettingsPage })))
const AdminPage = lazy(() => import('./pages/AdminPage').then(m => ({ default: m.AdminPage })))
const TrainingsPage = lazy(() => import('./pages/TrainingsPage').then(m => ({ default: m.TrainingsPage })))
const CampsPage = lazy(() => import('./pages/CampsPage').then(m => ({ default: m.CampsPage })))
const CoursesPage = lazy(() => import('./pages/CoursesPage').then(m => ({ default: m.CoursesPage })))

const LazyFallback = () => (
  <div className="flex justify-center py-12">
    <LoadingSpinner size="lg" />
  </div>
)

export default function App() {
  return (
    <ErrorBoundary>
      <ScrollToTop />
      <Suspense fallback={<LazyFallback />}>
        <Routes>
          <Route path="/" element={<Layout />}>
            <Route index element={<HomePage />} />
            <Route path="treningi" element={<TrainingsPage />} />
            <Route path="obozy" element={<CampsPage />} />
            <Route path="szkolenia" element={<CoursesPage />} />
            <Route path="login" element={<LoginPage />} />
            <Route path="register" element={<RegisterPage />} />
            <Route path="verify-email" element={<VerifyEmailPage />} />
            <Route path="forgot-password" element={<ForgotPasswordPage />} />
            <Route path="reset-password" element={<ResetPasswordPage />} />
            <Route path="resend-verification" element={<ResendVerificationPage />} />
            <Route path="oauth-callback" element={<OAuthCallbackPage />} />
            <Route
              path="settings"
              element={<ProtectedRoute><SettingsPage /></ProtectedRoute>}
            />
            <Route
              path="admin/*"
              element={<AdminRoute><AdminPage /></AdminRoute>}
            />
          </Route>
        </Routes>
      </Suspense>
    </ErrorBoundary>
  )
}
