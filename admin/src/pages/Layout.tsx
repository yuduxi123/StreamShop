import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { api } from '../api';

export default function Layout() {
  const navigate = useNavigate();

  const handleLogout = () => {
    api.logout();
    navigate('/login', { replace: true });
  };

  const linkClass = ({ isActive }: { isActive: boolean }) => isActive ? 'active' : '';

  return (
    <div className="page-layout">
      <div className="sidebar">
        <h2>StreamShop</h2>
        <nav>
          <NavLink to="/dashboard" className={linkClass}>数据看板</NavLink>
          <NavLink to="/videos" className={linkClass}>视频管理</NavLink>
          <NavLink to="/products" className={linkClass}>商品管理</NavLink>
          <NavLink to="/live-rooms" className={linkClass}>直播间管理</NavLink>
          <NavLink to="/orders" className={linkClass}>订单管理</NavLink>
          <NavLink to="/coupons" className={linkClass}>营销管理</NavLink>
        </nav>
        <button className="logout-btn" onClick={handleLogout}>退出登录</button>
      </div>
      <div className="main-area">
        <div className="topbar">StreamShop 管理后台</div>
        <div className="content">
          <Outlet />
        </div>
      </div>
    </div>
  );
}
