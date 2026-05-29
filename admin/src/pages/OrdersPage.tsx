import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api';

interface Order {
  id: string;
  status: string;
  total_amount: number;
  final_amount: number;
  created_at: string;
  items?: { product?: { title: string }; quantity: number }[];
}

const statuses = [
  { label: '全部', value: 'all' },
  { label: '待支付', value: 'pending' },
  { label: '已支付', value: 'paid' },
  { label: '已完成', value: 'completed' },
  { label: '已取消', value: 'cancelled' },
];

export default function OrdersPage() {
  const navigate = useNavigate();
  const [orders, setOrders] = useState<Order[]>([]);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState('all');
  const limit = 20;

  const load = async () => {
    setLoading(true);
    try {
      const data = await api.getOrders(page, limit, status);
      setOrders(data.data || []);
      setTotal(data.total || 0);
    } catch { /* ignore */ }
    setLoading(false);
  };

  useEffect(() => { load(); }, [page, status]);

  const handlePay = async (id: string) => {
    try { await api.payOrder(id); load(); } catch { /* ignore */ }
  };

  const handleCancel = async (id: string) => {
    if (!confirm('确定取消该订单？')) return;
    try { await api.cancelOrder(id); load(); } catch { /* ignore */ }
  };

  const totalPages = Math.ceil(total / limit);

  return (
    <div>
      <div className="toolbar">
        <h2 style={{ fontSize: 18, fontWeight: 600 }}>订单管理</h2>
        <div className="spacer" />
        {statuses.map(s => (
          <button
            key={s.value}
            className={`btn btn-sm ${status === s.value ? 'btn-primary' : 'btn-default'}`}
            onClick={() => { setStatus(s.value); setPage(1); }}
          >
            {s.label}
          </button>
        ))}
      </div>

      <div className="card table-wrap">
        {loading ? <div className="loading">加载中...</div> : (
          <table>
            <thead>
              <tr>
                <th>订单号</th>
                <th>商品</th>
                <th>总金额</th>
                <th>实付金额</th>
                <th>状态</th>
                <th>下单时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {orders.map(o => (
                <tr key={o.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/orders/${o.id}`)}>
                  <td style={{ fontFamily: 'monospace', fontSize: 12 }}>{o.id.substring(0, 12)}...</td>
                  <td style={{ maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {o.items?.slice(0, 2).map(i => i.product?.title).join(', ') || '-'}
                    {(o.items?.length ?? 0) > 2 ? ` 等${o.items!.length}件` : ''}
                  </td>
                  <td>¥{o.total_amount}</td>
                  <td><strong>¥{o.final_amount}</strong></td>
                  <td onClick={e => e.stopPropagation()}>
                    <span className={`status-badge ${o.status}`}>
                      {{ pending: '待支付', paid: '待发货', shipped: '已发货', completed: '已完成', cancelled: '已取消' }[o.status] || o.status}
                    </span>
                  </td>
                  <td style={{ fontSize: 12, color: '#999' }}>{o.created_at ? new Date(o.created_at).toLocaleString() : '-'}</td>
                  <td onClick={e => e.stopPropagation()}>
                    {o.status === 'pending' && <>
                      <button className="btn btn-primary btn-sm" onClick={() => handlePay(o.id)}>支付</button>{' '}
                      <button className="btn btn-default btn-sm" onClick={() => handleCancel(o.id)}>取消</button>
                    </>}
                    <button className="btn btn-default btn-sm" onClick={() => navigate(`/orders/${o.id}`)}>详情</button>
                  </td>
                </tr>
              ))}
              {orders.length === 0 && <tr><td colSpan={7}><div className="empty">暂无数据</div></td></tr>}
            </tbody>
          </table>
        )}
      </div>

      {totalPages > 1 && (
        <div className="pagination">
          <button disabled={page <= 1} onClick={() => setPage(p => p - 1)}>上一页</button>
          <span>第 {page} / {totalPages} 页</span>
          <button disabled={page >= totalPages} onClick={() => setPage(p => p + 1)}>下一页</button>
        </div>
      )}
    </div>
  );
}
