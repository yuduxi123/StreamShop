import { useEffect, useState } from 'react';
import { api } from '../api';

interface Coupon {
  id: string;
  title: string;
  type: 'fixed' | 'percentage';
  value: number;
  minPurchase: number;
  stock: number;
  validFrom: string;
  validTo: string;
}

export default function CouponsPage() {
  const [tab, setTab] = useState<'coupons' | 'flash'>('coupons');
  const [coupons, setCoupons] = useState<Coupon[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState<{ title: string; type: 'fixed' | 'percentage'; value: number; minPurchase: number; stock: number; validFrom: string; validTo: string }>({
    title: '',
    type: 'fixed',
    value: 0,
    minPurchase: 0,
    stock: 100,
    validFrom: '',
    validTo: '',
  });

  const loadCoupons = async () => {
    setLoading(true);
    try {
      const res = await fetch('http://localhost:3000/api/coupons');
      setCoupons(await res.json());
    } catch { /* ignore */ }
    setLoading(false);
  };

  useEffect(() => { loadCoupons(); }, []);

  const handleCreate = async () => {
    try {
      await fetch('http://localhost:3000/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'admin', password: 'admin123' })
      });
      const token = api.getToken();
      await fetch('http://localhost:3000/api/coupons', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify(form),
      });
      setShowModal(false);
      loadCoupons();
    } catch { /* ignore */ }
  };

  return (
    <div>
      <div className="toolbar">
        <h2 style={{ fontSize: 18, fontWeight: 600 }}>营销管理</h2>
        <div className="spacer" />
        <button className={`btn btn-sm ${tab === 'coupons' ? 'btn-primary' : 'btn-default'}`} onClick={() => setTab('coupons')}>优惠券</button>
        <button className={`btn btn-sm ${tab === 'flash' ? 'btn-primary' : 'btn-default'}`} onClick={() => setTab('flash')}>秒杀活动</button>
      </div>

      {tab === 'coupons' && (
        <>
          <div className="toolbar">
            <button className="btn btn-primary" onClick={() => setShowModal(true)}>+ 新增优惠券</button>
          </div>
          <div className="card table-wrap">
            {loading ? <div className="loading">加载中...</div> : (
              <table>
                <thead>
                  <tr>
                    <th>标题</th>
                    <th>类型</th>
                    <th>面值</th>
                    <th>最低消费</th>
                    <th>库存</th>
                    <th>有效期</th>
                  </tr>
                </thead>
                <tbody>
                  {coupons.map(c => (
                    <tr key={c.id}>
                      <td>{c.title}</td>
                      <td><span className="tag">{c.type === 'fixed' ? '满减' : '折扣'}</span></td>
                      <td>{c.type === 'fixed' ? `¥${c.value}` : `${c.value}折`}</td>
                      <td>¥{c.minPurchase}</td>
                      <td>{c.stock}</td>
                      <td style={{ fontSize: 12, color: '#999' }}>{c.validFrom} ~ {c.validTo}</td>
                    </tr>
                  ))}
                  {coupons.length === 0 && <tr><td colSpan={6}><div className="empty">暂无优惠券</div></td></tr>}
                </tbody>
              </table>
            )}
          </div>
        </>
      )}

      {tab === 'flash' && <FlashSaleSection />}

      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3>新增优惠券</h3>
              <button className="close" onClick={() => setShowModal(false)}>&times;</button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label>标题</label>
                <input value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} />
              </div>
              <div style={{ display: 'flex', gap: 12 }}>
                <div className="form-group" style={{ flex: 1 }}>
                  <label>类型</label>
                  <select value={form.type} onChange={e => setForm({ ...form, type: e.target.value as 'fixed' | 'percentage' })}>
                    <option value="fixed">满减</option>
                    <option value="percentage">折扣</option>
                  </select>
                </div>
                <div className="form-group" style={{ flex: 1 }}>
                  <label>面值</label>
                  <input type="number" value={form.value} onChange={e => setForm({ ...form, value: parseFloat(e.target.value) || 0 })} />
                </div>
              </div>
              <div style={{ display: 'flex', gap: 12 }}>
                <div className="form-group" style={{ flex: 1 }}>
                  <label>最低消费</label>
                  <input type="number" value={form.minPurchase} onChange={e => setForm({ ...form, minPurchase: parseFloat(e.target.value) || 0 })} />
                </div>
                <div className="form-group" style={{ flex: 1 }}>
                  <label>库存</label>
                  <input type="number" value={form.stock} onChange={e => setForm({ ...form, stock: parseInt(e.target.value) || 0 })} />
                </div>
              </div>
              <div style={{ display: 'flex', gap: 12 }}>
                <div className="form-group" style={{ flex: 1 }}>
                  <label>开始日期</label>
                  <input type="date" value={form.validFrom} onChange={e => setForm({ ...form, validFrom: e.target.value })} />
                </div>
                <div className="form-group" style={{ flex: 1 }}>
                  <label>结束日期</label>
                  <input type="date" value={form.validTo} onChange={e => setForm({ ...form, validTo: e.target.value })} />
                </div>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-default" onClick={() => setShowModal(false)}>取消</button>
              <button className="btn btn-primary" onClick={handleCreate}>创建</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function FlashSaleSection() {
  const [sales, setSales] = useState<any[]>([]);
  const [products, setProducts] = useState<any[]>([]);
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState({ productId: '', flashPrice: 0, stock: 10, maxPerUser: 1, startTime: '', endTime: '' });

  const load = async () => {
    try {
      const [sRes, pRes] = await Promise.all([
        fetch('http://localhost:3000/api/flash-sales'),
        fetch('http://localhost:3000/api/products?page=1&limit=100'),
      ]);
      setSales(await sRes.json());
      const pData = await pRes.json();
      setProducts(pData.data || []);
    } catch { /* ignore */ }
  };

  useEffect(() => { load(); }, []);

  const handleCreate = async () => {
    try {
      const token = api.getToken();
      await fetch('http://localhost:3000/api/flash-sales', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify(form),
      });
      setShowModal(false);
      load();
    } catch { /* ignore */ }
  };

  return (
    <div>
      <div className="toolbar">
        <button className="btn btn-primary" onClick={() => setShowModal(true)}>+ 新建秒杀</button>
      </div>
      <div className="card table-wrap">
        <table>
          <thead>
            <tr>
              <th>商品</th>
              <th>秒杀价</th>
              <th>库存</th>
              <th>每人限购</th>
              <th>开始</th>
              <th>结束</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            {sales.map(s => (
              <tr key={s.id}>
                <td style={{ maxWidth: 150, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{s.product?.title || s.productId}</td>
                <td style={{ color: '#ff4d4f', fontWeight: 600 }}>¥{s.flashPrice}</td>
                <td>{s.stock}</td>
                <td>{s.maxPerUser}</td>
                <td style={{ fontSize: 12 }}>{s.startTime ? new Date(s.startTime).toLocaleString() : '-'}</td>
                <td style={{ fontSize: 12 }}>{s.endTime ? new Date(s.endTime).toLocaleString() : '-'}</td>
                <td><span className={`status-badge ${s.status === 'active' ? 'active' : s.status === 'upcoming' ? 'pending' : 'inactive'}`}>{s.status === 'active' ? '进行中' : s.status === 'upcoming' ? '即将开始' : '已结束'}</span></td>
              </tr>
            ))}
            {sales.length === 0 && <tr><td colSpan={7}><div className="empty">暂无秒杀活动</div></td></tr>}
          </tbody>
        </table>
      </div>

      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3>新建秒杀活动</h3>
              <button className="close" onClick={() => setShowModal(false)}>&times;</button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label>商品</label>
                <select value={form.productId} onChange={e => setForm({ ...form, productId: e.target.value })}>
                  <option value="">请选择</option>
                  {products.map(p => <option key={p.id} value={p.id}>{p.title} - ¥{p.price}</option>)}
                </select>
              </div>
              <div style={{ display: 'flex', gap: 12 }}>
                <div className="form-group" style={{ flex: 1 }}>
                  <label>秒杀价</label>
                  <input type="number" step="0.01" value={form.flashPrice} onChange={e => setForm({ ...form, flashPrice: parseFloat(e.target.value) || 0 })} />
                </div>
                <div className="form-group" style={{ flex: 1 }}>
                  <label>秒杀库存</label>
                  <input type="number" value={form.stock} onChange={e => setForm({ ...form, stock: parseInt(e.target.value) || 0 })} />
                </div>
              </div>
              <div className="form-group">
                <label>每人限购</label>
                <input type="number" value={form.maxPerUser} onChange={e => setForm({ ...form, maxPerUser: parseInt(e.target.value) || 1 })} />
              </div>
              <div style={{ display: 'flex', gap: 12 }}>
                <div className="form-group" style={{ flex: 1 }}>
                  <label>开始时间</label>
                  <input type="datetime-local" value={form.startTime} onChange={e => setForm({ ...form, startTime: e.target.value })} />
                </div>
                <div className="form-group" style={{ flex: 1 }}>
                  <label>结束时间</label>
                  <input type="datetime-local" value={form.endTime} onChange={e => setForm({ ...form, endTime: e.target.value })} />
                </div>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-default" onClick={() => setShowModal(false)}>取消</button>
              <button className="btn btn-primary" onClick={handleCreate}>创建</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
