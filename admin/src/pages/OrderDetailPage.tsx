import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { api } from '../api';

interface OrderItem {
  product?: { id: string; title: string; cover_url: string; price: number };
  quantity: number;
  unit_price: number;
  subtotal: number;
}

interface Order {
  id: string;
  user_id: string;
  status: string;
  total_amount: number;
  discount: number;
  final_amount: number;
  shipping_address: string;
  created_at: string;
  items?: OrderItem[];
}

const statusMap: Record<string, string> = {
  pending: '待支付', paid: '待发货', shipped: '已发货', completed: '已完成', cancelled: '已取消',
};

export default function OrderDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    api.getOrder(id).then(setOrder).catch(() => {}).finally(() => setLoading(false));
  }, [id]);

  if (loading) return <div className="loading">加载中...</div>;
  if (!order) return <div className="empty">订单不存在</div>;

  return (
    <div>
      <div className="toolbar">
        <button className="btn btn-default" onClick={() => navigate('/orders')}>&larr; 返回订单列表</button>
        <h2 style={{ fontSize: 18, fontWeight: 600 }}>订单详情</h2>
      </div>

      <div className="detail-card">
        <h3>基本信息</h3>
        <div className="detail-row">
          <span className="label">订单号</span>
          <span className="value" style={{ fontFamily: 'monospace' }}>{order.id}</span>
        </div>
        <div className="detail-row">
          <span className="label">状态</span>
          <span className="value"><span className={`status-badge ${order.status}`}>{statusMap[order.status] || order.status}</span></span>
        </div>
        <div className="detail-row">
          <span className="label">用户 ID</span>
          <span className="value">{order.user_id}</span>
        </div>
        <div className="detail-row">
          <span className="label">收货地址</span>
          <span className="value">{order.shipping_address || '-'}</span>
        </div>
        <div className="detail-row">
          <span className="label">下单时间</span>
          <span className="value">{order.created_at ? new Date(order.created_at).toLocaleString() : '-'}</span>
        </div>
      </div>

      <div className="detail-card">
        <h3>商品明细</h3>
        {order.items?.map((item, i) => (
          <div className="product-item" key={i}>
            <img src={item.product?.cover_url || ''} alt="" className="thumb-sm" />
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 14 }}>{item.product?.title || '未知商品'}</div>
              <div style={{ fontSize: 12, color: '#999', marginTop: 2 }}>¥{item.unit_price} x {item.quantity}</div>
            </div>
            <div style={{ fontWeight: 600, color: '#FF3B30' }}>¥{item.subtotal}</div>
          </div>
        )) || <div className="empty">暂无商品</div>}
      </div>

      <div className="detail-card">
        <h3>金额信息</h3>
        <div className="detail-row">
          <span className="label">商品总额</span>
          <span className="value">¥{order.total_amount}</span>
        </div>
        <div className="detail-row">
          <span className="label">优惠</span>
          <span className="value" style={{ color: '#52c41a' }}>-¥{order.discount || 0}</span>
        </div>
        <div className="detail-row">
          <span className="label">实付金额</span>
          <span className="value" style={{ fontSize: 18, fontWeight: 700, color: '#FF3B30' }}>¥{order.final_amount}</span>
        </div>
      </div>
    </div>
  );
}
