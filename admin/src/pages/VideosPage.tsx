import { useEffect, useState } from 'react';
import { api } from '../api';

interface Video {
  id: string;
  title: string;
  coverUrl: string;
  videoUrl: string;
  status: string;
  viewCount: number;
  likeCount: number;
  tags: string[];
  products?: { id: string; title: string }[];
  author?: { username: string };
}

interface Product {
  id: string;
  title: string;
}

const STATUS_OPTIONS = [
  { value: 'draft', label: '草稿' },
  { value: 'published', label: '已发布' },
  { value: 'taken_down', label: '已下架' },
];

const STATUS_BADGE: Record<string, string> = {
  draft: 'status-badge draft',
  published: 'status-badge published',
  taken_down: 'status-badge taken-down',
};

const STATUS_LABEL: Record<string, string> = {
  draft: '草稿',
  published: '已发布',
  taken_down: '已下架',
};

export default function VideosPage() {
  const [videos, setVideos] = useState<Video[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<Video | null>(null);
  const [form, setForm] = useState({ title: '', coverUrl: '', videoUrl: '', status: 'draft', tags: '' });
  const [bindVideoId, setBindVideoId] = useState<string | null>(null);
  const [bindProductId, setBindProductId] = useState('');
  const limit = 20;

  const load = async () => {
    setLoading(true);
    try {
      const data = await api.getVideos(page, limit);
      setVideos(data.data || []);
      setTotal(data.total || 0);
    } catch {
      // ignore
    }
    setLoading(false);
  };

  const loadProducts = async () => {
    try {
      const data = await api.getProducts(1, 200);
      setProducts(data.data || []);
    } catch {
      // ignore
    }
  };

  useEffect(() => { load(); }, [page]);
  useEffect(() => { if (showModal || bindVideoId) loadProducts(); }, [showModal, bindVideoId]);

  const openCreate = () => {
    setEditing(null);
    setForm({ title: '', coverUrl: '', videoUrl: '', status: 'draft', tags: '' });
    setShowModal(true);
  };

  const openEdit = (v: Video) => {
    setEditing(v);
    setForm({
      title: v.title,
      coverUrl: v.coverUrl || '',
      videoUrl: v.videoUrl || '',
      status: v.status || 'draft',
      tags: (v.tags || []).join(', '),
    });
    setShowModal(true);
  };

  const handleSave = async () => {
    try {
      const payload: any = {
        title: form.title,
        coverUrl: form.coverUrl,
        videoUrl: form.videoUrl,
        status: form.status,
        tags: form.tags.split(',').map(t => t.trim()).filter(Boolean),
      };
      if (editing) {
        await api.updateVideo(editing.id, payload);
      } else {
        await api.createVideo(payload);
      }
      setShowModal(false);
      load();
    } catch {
      // ignore
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('确定删除该视频？')) return;
    try {
      await api.deleteVideo(id);
      load();
    } catch {
      // ignore
    }
  };

  const handleBindProduct = async () => {
    if (!bindVideoId || !bindProductId) return;
    try {
      await api.bindVideo(bindProductId, bindVideoId);
      setBindVideoId(null);
      setBindProductId('');
      load();
    } catch {
      // ignore
    }
  };

  const totalPages = Math.ceil(total / limit);

  return (
    <div>
      <div className="toolbar">
        <h2 style={{ fontSize: 18, fontWeight: 600 }}>短视频管理</h2>
        <div className="spacer" />
        <button className="btn btn-primary" onClick={openCreate}>+ 新建视频</button>
      </div>

      <div className="card table-wrap">
        {loading ? <div className="loading">加载中...</div> : (
          <table>
            <thead>
              <tr>
                <th>封面</th>
                <th>标题</th>
                <th>作者</th>
                <th>关联商品</th>
                <th>播放量</th>
                <th>点赞</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {videos.map(v => (
                <tr key={v.id}>
                  <td><img src={v.coverUrl} alt="" className="thumb" /></td>
                  <td style={{ maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{v.title}</td>
                  <td>{v.author?.username || '-'}</td>
                  <td>
                    {(v.products && v.products.length > 0)
                      ? v.products.map(p => <span key={p.id} className="tag tag-blue" style={{ margin: 1 }}>{p.title}</span>)
                      : <span style={{ color: '#ccc' }}>无</span>
                    }
                  </td>
                  <td>{v.viewCount ?? 0}</td>
                  <td>{v.likeCount ?? 0}</td>
                  <td><span className={STATUS_BADGE[v.status] || 'status-badge draft'}>{STATUS_LABEL[v.status] || v.status}</span></td>
                  <td>
                    <button className="btn btn-default btn-sm" onClick={() => openEdit(v)}>编辑</button>{' '}
                    <button className="btn btn-default btn-sm" onClick={() => setBindVideoId(v.id)}>绑定商品</button>{' '}
                    <button className="btn btn-danger btn-sm" onClick={() => handleDelete(v.id)}>删除</button>
                  </td>
                </tr>
              ))}
              {videos.length === 0 && <tr><td colSpan={8}><div className="empty">暂无数据</div></td></tr>}
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

      {/* Create / Edit Modal */}
      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3>{editing ? '编辑视频' : '新建视频'}</h3>
              <button className="close" onClick={() => setShowModal(false)}>&times;</button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label>标题 *</label>
                <input value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} placeholder="视频标题" />
              </div>
              <div className="form-group">
                <label>封面图片 URL</label>
                <input value={form.coverUrl} onChange={e => setForm({ ...form, coverUrl: e.target.value })} placeholder="https://..." />
              </div>
              <div className="form-group">
                <label>视频地址 *</label>
                <input value={form.videoUrl} onChange={e => setForm({ ...form, videoUrl: e.target.value })} placeholder="https://...mp4" />
              </div>
              <div className="form-group">
                <label>标签（逗号分隔）</label>
                <input value={form.tags} onChange={e => setForm({ ...form, tags: e.target.value })} placeholder="穿搭, 夏季, T恤" />
              </div>
              <div className="form-group">
                <label>状态</label>
                <select value={form.status} onChange={e => setForm({ ...form, status: e.target.value })}>
                  {STATUS_OPTIONS.map(opt => (
                    <option key={opt.value} value={opt.value}>{opt.label}</option>
                  ))}
                </select>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-default" onClick={() => setShowModal(false)}>取消</button>
              <button className="btn btn-primary" onClick={handleSave}>保存</button>
            </div>
          </div>
        </div>
      )}

      {/* Bind Product Modal */}
      {bindVideoId && (
        <div className="modal-overlay" onClick={() => { setBindVideoId(null); setBindProductId(''); }}>
          <div className="modal" onClick={e => e.stopPropagation()} style={{ width: 400 }}>
            <div className="modal-header">
              <h3>绑定商品到视频</h3>
              <button className="close" onClick={() => { setBindVideoId(null); setBindProductId(''); }}>&times;</button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label>选择商品</label>
                <select value={bindProductId} onChange={e => setBindProductId(e.target.value)}>
                  <option value="">请选择</option>
                  {products.map(p => <option key={p.id} value={p.id}>{p.title}</option>)}
                </select>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-default" onClick={() => { setBindVideoId(null); setBindProductId(''); }}>取消</button>
              <button className="btn btn-primary" disabled={!bindProductId} onClick={handleBindProduct}>绑定</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
