import { Suspense } from 'react'
import { lazyWithReload } from './utils/lazyWithReload'
import { Routes, Route, Navigate } from 'react-router-dom'
import { Layout } from './components/layout/Layout'
import { HomePage } from './pages/HomePage'
import { ProtectedRoute } from './components/layout/ProtectedRoute'
import { AdminRoute } from './components/layout/AdminRoute'
import { ErrorBoundary } from './components/ui/ErrorBoundary'
import { ScrollToTop } from './components/ScrollToTop'
import { LoadingSpinner } from './components/ui/LoadingSpinner'

const LoginPage = lazyWithReload(() => import('./pages/LoginPage').then(m => ({ default: m.LoginPage })))
const RegisterPage = lazyWithReload(() => import('./pages/RegisterPage').then(m => ({ default: m.RegisterPage })))
const VerifyEmailPage = lazyWithReload(() => import('./pages/VerifyEmailPage').then(m => ({ default: m.VerifyEmailPage })))
const ForgotPasswordPage = lazyWithReload(() => import('./pages/ForgotPasswordPage').then(m => ({ default: m.ForgotPasswordPage })))
const ResetPasswordPage = lazyWithReload(() => import('./pages/ResetPasswordPage').then(m => ({ default: m.ResetPasswordPage })))
const ResendVerificationPage = lazyWithReload(() => import('./pages/ResendVerificationPage').then(m => ({ default: m.ResendVerificationPage })))
const OAuthCallbackPage = lazyWithReload(() => import('./pages/OAuthCallbackPage').then(m => ({ default: m.OAuthCallbackPage })))
const SettingsPage = lazyWithReload(() => import('./pages/SettingsPage').then(m => ({ default: m.SettingsPage })))
const ProfileCompletionPage = lazyWithReload(() => import('./pages/ProfileCompletionPage').then(m => ({ default: m.ProfileCompletionPage })))
const MojeKontoPage = lazyWithReload(() => import('./pages/MojeKontoPage').then(m => ({ default: m.MojeKontoPage })))
const MojeRezerwacjePage = lazyWithReload(() => import('./pages/MojeRezerwacjePage').then(m => ({ default: m.MojeRezerwacjePage })))
const MojeTreningiPage = lazyWithReload(() => import('./pages/MojeTreningiPage').then(m => ({ default: m.MojeTreningiPage })))
const AdminPage = lazyWithReload(() => import('./pages/AdminPage').then(m => ({ default: m.AdminPage })))
const TrainingsPage = lazyWithReload(() => import('./pages/TrainingsPage').then(m => ({ default: m.TrainingsPage })))
const CampsPage = lazyWithReload(() => import('./pages/CampsPage').then(m => ({ default: m.CampsPage })))
const CoursesPage = lazyWithReload(() => import('./pages/CoursesPage').then(m => ({ default: m.CoursesPage })))
const PrivacyPolicyPage = lazyWithReload(() => import('./pages/PrivacyPolicyPage').then(m => ({ default: m.PrivacyPolicyPage })))
const MarketingUnsubscribePage = lazyWithReload(() => import('./pages/MarketingUnsubscribePage').then(m => ({ default: m.MarketingUnsubscribePage })))
const EventTypeDetailPage = lazyWithReload(() => import('./pages/EventTypeDetailPage').then(m => ({ default: m.EventTypeDetailPage })))
const EventDetailPage = lazyWithReload(() => import('./pages/EventDetailPage').then(m => ({ default: m.EventDetailPage })))
const InstructorDetailPage = lazyWithReload(() => import('./pages/InstructorDetailPage').then(m => ({ default: m.InstructorDetailPage })))

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
            <Route path="kadra/:id" element={<InstructorDetailPage />} />
            <Route path=":categorySlug/rodzaj/:id" element={<EventTypeDetailPage />} />
            <Route path=":categorySlug/termin/:id" element={<EventDetailPage />} />
            <Route path="polityka-prywatnosci" element={<PrivacyPolicyPage />} />
            <Route path="wypisz-sie" element={<MarketingUnsubscribePage />} />
            <Route path="verify-email" element={<VerifyEmailPage />} />
            <Route path="reset-password" element={<ResetPasswordPage />} />
            <Route path="forgot-password" element={<ForgotPasswordPage />} />
            <Route path="resend-verification" element={<ResendVerificationPage />} />
            <Route path="oauth-callback" element={<OAuthCallbackPage />} />
            <Route path="logowanie" element={<LoginPage />} />
            <Route path="rejestracja" element={<RegisterPage />} />
            <Route
              path="moje-konto"
              element={<ProtectedRoute><MojeKontoPage /></ProtectedRoute>}
            />
            <Route
              path="moje-konto/rezerwacje"
              element={<ProtectedRoute><MojeRezerwacjePage /></ProtectedRoute>}
            />
            <Route
              path="moje-konto/treningi"
              element={<ProtectedRoute><MojeTreningiPage /></ProtectedRoute>}
            />
            <Route
              path="settings"
              element={<ProtectedRoute><SettingsPage /></ProtectedRoute>}
            />
            <Route
              path="uzupelnij-profil"
              element={<ProtectedRoute><ProfileCompletionPage /></ProtectedRoute>}
            />
            <Route
              path="admin/:tab"
              element={<AdminRoute><AdminPage /></AdminRoute>}
            />
            <Route
              path="admin"
              element={<AdminRoute><Navigate to="/admin/treningi" replace /></AdminRoute>}
            />
            <Route path="login" element={<Navigate to="/logowanie" replace />} />
            <Route path="register" element={<Navigate to="/rejestracja" replace />} />
            <Route path="admin/login" element={<Navigate to="/logowanie" replace />} />
            <Route path="admin/register" element={<Navigate to="/rejestracja" replace />} />
          </Route>
        </Routes>
      </Suspense>
    </ErrorBoundary>
  )
}
