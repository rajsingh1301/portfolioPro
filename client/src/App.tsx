import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'

import { ProtectedRoute } from './components/ProtectedRoute'
import { Shell } from './components/shell/Shell'
import { AuthProvider } from './context/AuthProvider'
import { ThemeProvider } from './context/ThemeProvider'
import { ChartsPage } from './pages/ChartsPage'
import { DashboardPage } from './pages/DashboardPage'
import { Login } from './pages/Login'
import { OrdersPage } from './pages/OrdersPage'
import { PortfolioPage } from './pages/PortfolioPage'
import { RiskPage } from './pages/RiskPage'
import { Signup } from './pages/Signup'
import { WatchlistPage } from './pages/WatchlistPage'

export default function App() {
  return (
    <ThemeProvider>
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/signup" element={<Signup />} />
            <Route element={<ProtectedRoute />}>
              <Route element={<Shell />}>
                <Route path="/" element={<DashboardPage />} />
                <Route path="/charts" element={<ChartsPage />} />
                <Route path="/portfolio" element={<PortfolioPage />} />
                <Route path="/orders" element={<OrdersPage />} />
                <Route path="/watchlist" element={<WatchlistPage />} />
                <Route path="/risk" element={<RiskPage />} />
              </Route>
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </ThemeProvider>
  )
}
