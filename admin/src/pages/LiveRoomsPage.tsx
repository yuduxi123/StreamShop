import { useEffect, useState } from 'react';
import { api } from '../api';

interface Room {
  id: string;
  title: string;
  cover_url: string;
  status: string;
  online_count: number;
  anchor?: string;
}

interface Product {
  id: string;
  title: string;
}

export default function LiveRoomsPage() {
  const [rooms, setRooms] = useState<Room[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<Room | null>(null);
  const [form, setForm] = useState({ title: '', cover_url: '', anchor: '' });
  const [productRoomId, setProductRoomId] = useState<string | null>(null);
  const [productId, setProductId] = useState('');

  const load = async () => {
    setLoading(true);
    try {
      const data = await api.getRooms(1, 50);
      setRooms(data.data || []);
    } catch { /* ignore */ }
    setLoading(false);
  };

  const loadProducts = async () => {
    try {
      const data = await api.getProducts(1, 200);
      setProducts(data.data || []);
    } catch { /* ignore */ }
  };

  useEffect(() => { load(); }, []);
  useEffect(() => { if (showModal || productRoomId) loadProducts(); }, [showModal, productRoomId]);

  const openCreate = () => {
    setEditing(null);
    setForm({ title: '', cover_url: '', anchor: '' });
    setShowModal(true);
  };

  const openEdit = (r: Room) => {
    setEditing(r);
    setForm({ title: r.title, cover_url: r.cover_url, anchor: r.anchor || '' });
    setShowModal(true);
  };

  const handleSave = async () => {
    try {
      if (editing) {
        await api.updateRoom(editing.id, form);
      } else {
        await api.createRoom(form);
      }
      setShowModal(false);
      load();
    } catch { /* ignore */ }
  };

  const handleStart = async (id: string) => {
    try { await api.startRoom(id); load(); } catch { /* ignore */ }
  };

  const handleEnd = async (id: string) => {
    try { await api.endRoom(id); load(); } catch { /* ignore */ }
  };

  const handleAddProduct = async () => {
    if (!productRoomId || !productId) return;
    try {
      await api.addRoomProduct(productRoomId, productId);
      setProductRoomId(null);
      setProductId('');
    } catch { /* ignore */ }
  };

  return (
    <div>
      <div className="toolbar">
        <h2 style={{ fontSize: 18, fontWeight: 600 }}>直播间管理</h2>
        <div className="spacer" />
        <button className="btn btn-primary" onClick={openCreate}>+ 新增直播间</button>
      </div>

      <div className="card table-wrap">
        {loading ? <div className="loading">加载中...</div> : (
          <table>
            <thead>
              <tr>
                <th>封面</th>
                <th>标题</th>
                <th>主播</th>
                <th>在线人数</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {rooms.map(r => (
                <tr key={r.id}>
                  <td><img src={r.cover_url} alt="" className="thumb" /></td>
                  <td>{r.title}</td>
                  <td>{r.anchor || '-'}</td>
                  <td>{r.online_count ?? 0}</td>
                  <td>
                    <span className={`status-badge ${r.status === 'live' ? 'active' : r.status === 'ended' ? 'inactive' : ''}`}>
                      {r.status === 'live' ? '直播中' : r.status === 'ended' ? '已结束' : '待开始'}
                    </span>
                  </td>
                  <td>
                    {r.status === 'idle' && <button className="btn btn-success btn-sm" onClick={() => handleStart(r.id)}>开播</button>}
                    {r.status === 'live' && <button className="btn btn-warning btn-sm" onClick={() => handleEnd(r.id)}>结束</button>}
                    {' '}
                    <button className="btn btn-default btn-sm" onClick={() => openEdit(r)}>编辑</button>{' '}
                    <button className="btn btn-default btn-sm" onClick={() => setProductRoomId(r.id)}>添加商品</button>
                  </td>
                </tr>
              ))}
              {rooms.length === 0 && <tr><td colSpan={6}><div className="empty">暂无数据</div></td></tr>}
            </tbody>
          </table>
        )}
      </div>

      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3>{editing ? '编辑直播间' : '新增直播间'}</h3>
              <button className="close" onClick={() => setShowModal(false)}>&times;</button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label>标题</label>
                <input value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} />
              </div>
              <div className="form-group">
                <label>封面 URL</label>
                <input value={form.cover_url} onChange={e => setForm({ ...form, cover_url: e.target.value })} />
              </div>
              <div className="form-group">
                <label>主播</label>
                <input value={form.anchor} onChange={e => setForm({ ...form, anchor: e.target.value })} />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-default" onClick={() => setShowModal(false)}>取消</button>
              <button className="btn btn-primary" onClick={handleSave}>保存</button>
            </div>
          </div>
        </div>
      )}

      {productRoomId && (
        <div className="modal-overlay" onClick={() => { setProductRoomId(null); setProductId(''); }}>
          <div className="modal" onClick={e => e.stopPropagation()} style={{ width: 400 }}>
            <div className="modal-header">
              <h3>添加商品到直播间</h3>
              <button className="close" onClick={() => { setProductRoomId(null); setProductId(''); }}>&times;</button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label>选择商品</label>
                <select value={productId} onChange={e => setProductId(e.target.value)}>
                  <option value="">请选择</option>
                  {products.map(p => <option key={p.id} value={p.id}>{p.title}</option>)}
                </select>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-default" onClick={() => { setProductRoomId(null); setProductId(''); }}>取消</button>
              <button className="btn btn-primary" disabled={!productId} onClick={handleAddProduct}>添加</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
