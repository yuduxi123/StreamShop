import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Layout from './pages/Layout';
import LoginPage from './pages/LoginPage';
import VideosPage from './pages/VideosPage';
import ProductsPage from './pages/ProductsPage';
import LiveRoomsPage from './pages/LiveRoomsPage';
import OrdersPage from './pages/OrdersPage';
import OrderDetailPage from './pages/OrderDetailPage';
import DashboardPage from './pages/DashboardPage';
import CouponsPage from './pages/CouponsPage';
import { api } from './api';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  if (!api.isAuthenticated()) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<ProtectedRoute><Layout /></ProtectedRoute>}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="videos" element={<VideosPage />} />
          <Route path="products" element={<ProductsPage />} />
          <Route path="live-rooms" element={<LiveRoomsPage />} />
          <Route path="orders" element={<OrdersPage />} />
          <Route path="orders/:id" element={<OrderDetailPage />} />
          <Route path="coupons" element={<CouponsPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
