import { useEffect, useState } from 'react';
import { api } from '../api';

interface Product {
  id: string;
  title: string;
  price: number;
  originalPrice: number;
  coverUrl: string;
  stock: number;
  salesCount: number;
  category: string;
  status: string;
}

const categories = ['All', 'Fashion', 'Electronics', 'Food', 'Home', 'Beauty', 'Sports', 'Books', 'Other'];

export default function ProductsPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [category, setCategory] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<Product | null>(null);
  const [form, setForm] = useState({ title: '', price: 0, originalPrice: 0, coverUrl: '', stock: 0, category: 'Other', status: 'active' });
  const limit = 20;

  const load = async () => {
    setLoading(true);
    try {
      const data = await api.getProducts(page, limit, category || undefined);
      setProducts(data.data || []);
      setTotal(data.total || 0);
    } catch {
      // ignore
    }
    setLoading(false);
  };

  useEffect(() => { load(); }, [page, category]);

  const openCreate = () => {
    setEditing(null);
    setForm({ title: '', price: 0, originalPrice: 0, coverUrl: '', stock: 0, category: 'Other', status: 'active' });
    setShowModal(true);
  };

  const openEdit = (p: Product) => {
    setEditing(p);
    setForm({ title: p.title, price: p.price, originalPrice: p.originalPrice, coverUrl: p.coverUrl, stock: p.stock, category: p.category || 'Other', status: p.status });
    setShowModal(true);
  };

  const handleSave = async () => {
    try {
      if (editing) {
        await api.updateProduct(editing.id, form);
      } else {
        await api.createProduct(form);
      }
      setShowModal(false);
      load();
    } catch {
      // ignore
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Delete this product?')) return;
    try {
      await api.deleteProduct(id);
      load();
    } catch {
      // ignore
    }
  };

  const totalPages = Math.ceil(total / limit);

  return (
    <div>
      <div className="toolbar">
        <h2 style={{ fontSize: 18, fontWeight: 600 }}>Product Management</h2>
        <div className="spacer" />
        <select value={category} onChange={e => { setCategory(e.target.value); setPage(1); }}>
          {categories.map(c => <option key={c} value={c === 'All' ? '' : c}>{c}</option>)}
        </select>
        <button className="btn btn-primary" onClick={openCreate}>+ New Product</button>
      </div>

      <div className="card table-wrap">
        {loading ? <div className="loading">Loading...</div> : (
          <table>
            <thead>
              <tr>
                <th>Image</th>
                <th>Title</th>
                <th>Price</th>
                <th>Original</th>
                <th>Stock</th>
                <th>Sales</th>
                <th>Category</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {products.map(p => (
                <tr key={p.id}>
                  <td><img src={p.coverUrl} alt="" className="thumb" /></td>
                  <td style={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{p.title}</td>
                  <td>¥{p.price}</td>
                  <td style={{ textDecoration: 'line-through', color: '#999' }}>¥{p.originalPrice}</td>
                  <td>{p.stock}</td>
                  <td>{p.salesCount ?? 0}</td>
                  <td><span className="tag tag-blue">{p.category || 'Uncategorized'}</span></td>
                  <td><span className={`status-badge ${p.status === 'active' ? 'active' : 'inactive'}`}>{p.status === 'active' ? 'Active' : 'Inactive'}</span></td>
                  <td>
                    <button className="btn btn-default btn-sm" onClick={() => openEdit(p)}>Edit</button>{' '}
                    <button className="btn btn-danger btn-sm" onClick={() => handleDelete(p.id)}>Delete</button>
                  </td>
                </tr>
              ))}
              {products.length === 0 && <tr><td colSpan={9}><div className="empty">No data</div></td></tr>}
            </tbody>
          </table>
        )}
      </div>

      {totalPages > 1 && (
        <div className="pagination">
          <button disabled={page <= 1} onClick={() => setPage(p => p - 1)}>Prev</button>
          <span>Page {page} / {totalPages}</span>
          <button disabled={page >= totalPages} onClick={() => setPage(p => p + 1)}>Next</button>
        </div>
      )}

      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3>{editing ? 'Edit Product' : 'New Product'}</h3>
              <button className="close" onClick={() => setShowModal(false)}>&times;</button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label>Title</label>
                <input value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} />
              </div>
              <div style={{ display: 'flex', gap: 12 }}>
                <div className="form-group" style={{ flex: 1 }}>
                  <label>Price</label>
                  <input type="number" step="0.01" value={form.price} onChange={e => setForm({ ...form, price: parseFloat(e.target.value) || 0 })} />
                </div>
                <div className="form-group" style={{ flex: 1 }}>
                  <label>Original Price</label>
                  <input type="number" step="0.01" value={form.originalPrice} onChange={e => setForm({ ...form, originalPrice: parseFloat(e.target.value) || 0 })} />
                </div>
              </div>
              <div style={{ display: 'flex', gap: 12 }}>
                <div className="form-group" style={{ flex: 1 }}>
                  <label>Stock</label>
                  <input type="number" value={form.stock} onChange={e => setForm({ ...form, stock: parseInt(e.target.value) || 0 })} />
                </div>
                <div className="form-group" style={{ flex: 1 }}>
                  <label>Category</label>
                  <select value={form.category} onChange={e => setForm({ ...form, category: e.target.value })}>
                    {categories.filter(c => c !== 'All').map(c => <option key={c}>{c}</option>)}
                  </select>
                </div>
              </div>
              <div className="form-group">
                <label>Cover URL</label>
                <input value={form.coverUrl} onChange={e => setForm({ ...form, coverUrl: e.target.value })} />
              </div>
              <div className="form-group">
                <label>Status</label>
                <select value={form.status} onChange={e => setForm({ ...form, status: e.target.value })}>
                  <option value="active">Active</option>
                  <option value="inactive">Inactive</option>
                </select>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-default" onClick={() => setShowModal(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={handleSave}>Save</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
