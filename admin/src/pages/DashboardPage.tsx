import { useEffect, useState } from 'react';

interface Kpi { totalOrders: number; totalRevenue: number; totalProducts: number; totalVideos: number; totalViews: number; totalLikes: number; activeRooms: number; }
interface Funnel { pending: number; paid: number; completed: number; cancelled: number; total: number; }
interface MonthlyGmv { month: string; gmv: number; }
interface TopProduct { id: string; title: string; price: number; coverUrl: string; salesCount: number; category: string; }
interface ViewTrend { date: string; views: number; likes: number; }
interface DashData { kpi: Kpi; funnel: Funnel; monthlyGMV: MonthlyGmv[]; topProducts: TopProduct[]; viewsTrend: ViewTrend[]; }

export default function DashboardPage() {
  const [data, setData] = useState<DashData | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch('http://localhost:3000/api/stats/dashboard')
      .then(r => r.json())
      .then(setData)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="loading">加载中...</div>;
  if (!data) return <div className="empty">无法加载数据</div>;

  const { kpi, funnel, monthlyGMV, topProducts, viewsTrend } = data;

  return (
    <div>
      <h2 style={{ fontSize: 18, fontWeight: 600, marginBottom: 16 }}>数据看板</h2>

      {/* KPI Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: 12, marginBottom: 24 }}>
        {[
          { label: '总订单', value: kpi.totalOrders, color: '#1890ff' },
          { label: '总收入 (¥)', value: kpi.totalRevenue.toLocaleString(), color: '#52c41a' },
          { label: '商品数', value: kpi.totalProducts, color: '#722ed1' },
          { label: '视频数', value: kpi.totalVideos, color: '#fa8c16' },
          { label: '总播放', value: kpi.totalViews.toLocaleString(), color: '#eb2f96' },
          { label: '总点赞', value: kpi.totalLikes.toLocaleString(), color: '#ff4d4f' },
          { label: '直播中', value: kpi.activeRooms, color: '#13c2c2' },
        ].map(k => (
          <div key={k.label} className="card" style={{ padding: '16px', textAlign: 'center' }}>
            <div style={{ fontSize: 13, color: '#999', marginBottom: 4 }}>{k.label}</div>
            <div style={{ fontSize: 24, fontWeight: 700, color: k.color }}>{k.value}</div>
          </div>
        ))}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 24 }}>
        {/* GMV Trend */}
        <div className="card" style={{ padding: 16 }}>
          <h3 style={{ fontSize: 15, fontWeight: 600, marginBottom: 12 }}>近6月 GMV 趋势</h3>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8, height: 120, paddingTop: 8 }}>
            {monthlyGMV.map(m => {
              const max = Math.max(...monthlyGMV.map(x => x.gmv), 1);
              const h = (m.gmv / max) * 100;
              return (
                <div key={m.month} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
                  <div style={{ fontSize: 11, color: '#999' }}>¥{(m.gmv / 1000).toFixed(0)}k</div>
                  <div style={{ width: '100%', height: `${Math.max(h, 4)}%`, background: 'linear-gradient(180deg, #1890ff, #69c0ff)', borderRadius: '4px 4px 0 0', minHeight: 4 }} />
                  <div style={{ fontSize: 11, color: '#999' }}>{m.month.slice(5)}</div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Conversion Funnel */}
        <div className="card" style={{ padding: 16 }}>
          <h3 style={{ fontSize: 15, fontWeight: 600, marginBottom: 12 }}>转化漏斗</h3>
          {[
            { label: '全部订单', count: funnel.total, pct: 100 },
            { label: '待支付', count: funnel.pending, pct: funnel.total ? (funnel.pending / funnel.total * 100) : 0 },
            { label: '已支付', count: funnel.paid, pct: funnel.total ? (funnel.paid / funnel.total * 100) : 0 },
            { label: '已完成', count: funnel.completed, pct: funnel.total ? (funnel.completed / funnel.total * 100) : 0 },
            { label: '已取消', count: funnel.cancelled, pct: funnel.total ? (funnel.cancelled / funnel.total * 100) : 0 },
          ].map(f => (
            <div key={f.label} style={{ marginBottom: 8 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, marginBottom: 2 }}>
                <span>{f.label}</span>
                <span style={{ color: '#666' }}>{f.count} ({f.pct.toFixed(0)}%)</span>
              </div>
              <div style={{ height: 6, background: '#f0f0f0', borderRadius: 3, overflow: 'hidden' }}>
                <div style={{ width: `${f.pct}%`, height: '100%', background: f.label === '已取消' ? '#ff4d4f' : '#1890ff', borderRadius: 3 }} />
              </div>
            </div>
          ))}
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        {/* Views trend */}
        <div className="card" style={{ padding: 16 }}>
          <h3 style={{ fontSize: 15, fontWeight: 600, marginBottom: 12 }}>近14天播放/点赞趋势</h3>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 4, height: 100, paddingTop: 8 }}>
            {viewsTrend.map(v => {
              const maxViews = Math.max(...viewsTrend.map(x => x.views), 1);
              const maxLikes = Math.max(...viewsTrend.map(x => x.likes), 1);
              return (
                <div key={v.date} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
                  <div style={{ width: '100%', height: `${(v.views / maxViews) * 80}%`, background: '#1890ff', borderRadius: '2px 2px 0 0', minHeight: 2 }} />
                  <div style={{ width: '100%', height: `${(v.likes / maxLikes) * 60}%`, background: '#ff4d4f', borderRadius: '2px 2px 0 0', minHeight: 2 }} />
                  <div style={{ fontSize: 9, color: '#999', transform: 'rotate(-45deg)', marginTop: 4 }}>{v.date.slice(5)}</div>
                </div>
              );
            })}
          </div>
          <div style={{ display: 'flex', gap: 16, justifyContent: 'center', marginTop: 8, fontSize: 12 }}>
            <span><span style={{ color: '#1890ff' }}>■</span> 播放</span>
            <span><span style={{ color: '#ff4d4f' }}>■</span> 点赞</span>
          </div>
        </div>

        {/* Top Products */}
        <div className="card" style={{ padding: 16 }}>
          <h3 style={{ fontSize: 15, fontWeight: 600, marginBottom: 12 }}>热销 Top 10</h3>
          {topProducts.map((p, i) => (
            <div key={p.id} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 0', borderBottom: i < topProducts.length - 1 ? '1px solid #f5f5f5' : 'none' }}>
              <span style={{ width: 18, textAlign: 'center', fontWeight: 700, color: i < 3 ? '#ff4d4f' : '#999', fontSize: 13 }}>{i + 1}</span>
              <img src={p.coverUrl} alt="" style={{ width: 36, height: 36, borderRadius: 4, objectFit: 'cover', background: '#f5f5f5' }} />
              <div style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 13 }}>{p.title}</div>
              <span style={{ fontSize: 12, color: '#999' }}>销量 {p.salesCount}</span>
              <span style={{ fontSize: 12, fontWeight: 600, color: '#ff4d4f', minWidth: 50, textAlign: 'right' }}>¥{p.price}</span>
            </div>
          ))}
          {topProducts.length === 0 && <div className="empty">暂无数据</div>}
        </div>
      </div>
    </div>
  );
}
