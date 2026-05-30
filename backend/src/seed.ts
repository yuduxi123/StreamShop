import fs from 'fs';
import path from 'path';
import { v4 as uuidv4 } from 'uuid';
import bcrypt from 'bcryptjs';

const DATA_DIR = path.join(__dirname, 'data');

function write(filename: string, data: any): void {
  fs.writeFileSync(path.join(DATA_DIR, filename), JSON.stringify(data, null, 2), 'utf-8');
}

function ensureDir(): void {
  if (!fs.existsSync(DATA_DIR)) {
    fs.mkdirSync(DATA_DIR, { recursive: true });
  }
}

// UUIDs for consistent references
const ids = {
  user1: uuidv4(), user2: uuidv4(), admin: uuidv4(),
  video1: uuidv4(), video2: uuidv4(), video3: uuidv4(), video4: uuidv4(), video5: uuidv4(),
  product1: uuidv4(), product2: uuidv4(), product3: uuidv4(), product4: uuidv4(), product5: uuidv4(),
  product6: uuidv4(), product7: uuidv4(), product8: uuidv4(), product9: uuidv4(), product10: uuidv4(),
  live1: uuidv4(), live2: uuidv4(),
  coupon1: uuidv4(), coupon2: uuidv4(),
};

function run(): void {
  ensureDir();

  // Users
  const users = [
    { id: ids.user1, username: 'alice', avatarUrl: 'https://picsum.photos/seed/alice/200/200', role: 'user', password: bcrypt.hashSync('123456', 10), createdAt: '2026-01-01T00:00:00Z' },
    { id: ids.user2, username: 'bob', avatarUrl: 'https://picsum.photos/seed/bob/200/200', role: 'user', password: bcrypt.hashSync('123456', 10), createdAt: '2026-01-02T00:00:00Z' },
    { id: ids.admin, username: 'admin', avatarUrl: 'https://picsum.photos/seed/admin/200/200', role: 'admin', password: bcrypt.hashSync('admin123', 10), createdAt: '2026-01-01T00:00:00Z' },
  ];
  write('users.json', users);

  const now = new Date().toISOString();

  // Products
  const products = [
    { id: ids.product1, title: '极简纯棉T恤', description: '100%纯棉面料，舒适透气，四季百搭基础款', price: 89, originalPrice: 129, coverUrl: 'https://picsum.photos/seed/p1/400/400', stock: 500, salesCount: 1280, status: 'active', category: '服饰', createdAt: now },
    { id: ids.product2, title: '无线蓝牙耳机 Pro', description: '主动降噪，30小时续航，IPX5防水', price: 299, originalPrice: 499, coverUrl: 'https://picsum.photos/seed/p2/400/400', stock: 300, salesCount: 856, status: 'active', category: '数码', createdAt: now },
    { id: ids.product3, title: '轻奢护肤精华液', description: '玻尿酸补水保湿，修护肌肤屏障', price: 159, originalPrice: 239, coverUrl: 'https://picsum.photos/seed/p3/400/400', stock: 200, salesCount: 2340, status: 'active', category: '美妆', createdAt: now },
    { id: ids.product4, title: '智能运动手表', description: '心率血氧监测，GPS运动轨迹，7天续航', price: 399, originalPrice: 599, coverUrl: 'https://picsum.photos/seed/p4/400/400', stock: 150, salesCount: 567, status: 'active', category: '数码', createdAt: now },
    { id: ids.product5, title: '日式陶瓷餐具套装', description: '16件套，微波炉洗碗机可用', price: 129, originalPrice: 199, coverUrl: 'https://picsum.photos/seed/p5/400/400', stock: 400, salesCount: 923, status: 'active', category: '家居', createdAt: now },
    { id: ids.product6, title: '慢回弹记忆枕', description: '人体工学设计，改善颈椎睡眠', price: 79, originalPrice: 139, coverUrl: 'https://picsum.photos/seed/p6/400/400', stock: 600, salesCount: 1567, status: 'active', category: '家居', createdAt: now },
    { id: ids.product7, title: '速干运动短裤', description: '四面弹力，透气速干，跑步健身必备', price: 69, originalPrice: 119, coverUrl: 'https://picsum.photos/seed/p7/400/400', stock: 350, salesCount: 2345, status: 'active', category: '服饰', createdAt: now },
    { id: ids.product8, title: '便携式迷你风扇', description: '三档风力，静音运行，USB充电', price: 49, originalPrice: 89, coverUrl: 'https://picsum.photos/seed/p8/400/400', stock: 800, salesCount: 3456, status: 'active', category: '数码', createdAt: now },
    { id: ids.product9, title: '有机绿茶礼盒', description: '明前采摘，清香甘甜，送礼佳品', price: 99, originalPrice: 168, coverUrl: 'https://picsum.photos/seed/p9/400/400', stock: 250, salesCount: 789, status: 'active', category: '食品', createdAt: now },
    { id: ids.product10, title: 'ins风帆布背包', description: '大容量防水，多隔层设计，通勤出游', price: 109, originalPrice: 179, coverUrl: 'https://picsum.photos/seed/p10/400/400', stock: 200, salesCount: 1102, status: 'active', category: '服饰', createdAt: now },
  ];
  write('products.json', products);

  // Videos with product bindings
  const videoProducts = [
    { id: ids.video1 + '_' + ids.product1, videoId: ids.video1, productId: ids.product1, displayOrder: 0 },
    { id: ids.video1 + '_' + ids.product7, videoId: ids.video1, productId: ids.product7, displayOrder: 1 },
    { id: ids.video2 + '_' + ids.product2, videoId: ids.video2, productId: ids.product2, displayOrder: 0 },
    { id: ids.video2 + '_' + ids.product4, videoId: ids.video2, productId: ids.product4, displayOrder: 1 },
    { id: ids.video2 + '_' + ids.product8, videoId: ids.video2, productId: ids.product8, displayOrder: 2 },
    { id: ids.video3 + '_' + ids.product3, videoId: ids.video3, productId: ids.product3, displayOrder: 0 },
    { id: ids.video4 + '_' + ids.product5, videoId: ids.video4, productId: ids.product5, displayOrder: 0 },
    { id: ids.video4 + '_' + ids.product6, videoId: ids.video4, productId: ids.product6, displayOrder: 1 },
    { id: ids.video5 + '_' + ids.product9, videoId: ids.video5, productId: ids.product9, displayOrder: 0 },
    { id: ids.video5 + '_' + ids.product10, videoId: ids.video5, productId: ids.product10, displayOrder: 1 },
  ];
  write('video_products.json', videoProducts);

  // Use public sample videos — domestic CDN preferred for accessibility
  const videoUrls = [
    'https://sf1-cdn-tos.huoshanstatic.com/obj/media-fe/xgplayer_doc_video/mp4/xgplayer-demo-360p.mp4',       // 火山引擎CDN(西瓜视频Demo)
    'https://stream7.iqilu.com/10339/upload_transcode/202002/09/20200209105011F0zPoYzHry.mp4',                 // 齐鲁网新闻1
    'https://media.w3.org/2010/05/sintel/trailer.mp4',                                                        // W3C Sintel预告片
    'https://stream7.iqilu.com/10339/upload_transcode/202002/09/20200209104902N3v5Vpxuvb.mp4',                 // 齐鲁网新闻2
  ];

  const videos = [
    { id: ids.video1, title: '夏季穿搭推荐，纯棉T恤才是王道', coverUrl: 'https://picsum.photos/seed/v1/720/1280', videoUrl: videoUrls[0], authorId: ids.user1, status: 'published', viewCount: 15200, likeCount: 892, commentCount: 134, shareCount: 56, tags: ['穿搭', '夏季', 'T恤'], createdAt: '2026-05-20T10:00:00Z' },
    { id: ids.video2, title: '数码好物开箱！降噪耳机+运动手表', coverUrl: 'https://picsum.photos/seed/v2/720/1280', videoUrl: videoUrls[1], authorId: ids.user1, status: 'published', viewCount: 23100, likeCount: 1456, commentCount: 267, shareCount: 89, tags: ['数码', '开箱', '耳机'], createdAt: '2026-05-21T14:00:00Z' },
    { id: ids.video3, title: '护肤秘籍：精华液你用对了吗？', coverUrl: 'https://picsum.photos/seed/v3/720/1280', videoUrl: videoUrls[2], authorId: ids.user2, status: 'published', viewCount: 8900, likeCount: 567, commentCount: 98, shareCount: 34, tags: ['护肤', '美妆', '精华'], createdAt: '2026-05-22T09:00:00Z' },
    { id: ids.video4, title: '家居好物推荐，提升幸福感的小物件', coverUrl: 'https://picsum.photos/seed/v4/720/1280', videoUrl: videoUrls[3], authorId: ids.user2, status: 'published', viewCount: 6780, likeCount: 423, commentCount: 76, shareCount: 23, tags: ['家居', '好物'], createdAt: '2026-05-23T16:00:00Z' },
    { id: ids.video5, title: '平价好物合集，学生党必看', coverUrl: 'https://picsum.photos/seed/v5/720/1280', videoUrl: videoUrls[0], authorId: ids.user1, status: 'published', viewCount: 32100, likeCount: 2100, commentCount: 389, shareCount: 156, tags: ['平价', '好物', '学生'], createdAt: '2026-05-24T11:00:00Z' },
  ];
  write('videos.json', videos);

  // Live rooms
  const liveRooms = [
    { id: ids.live1, title: '夏日清凉专场，冰点价来袭！', coverUrl: 'https://picsum.photos/seed/l1/720/1280', anchorId: ids.user1, status: 'live', currentProductId: ids.product1, onlineCount: 1250, likeCount: 34500, viewerCount: 8900, createdAt: now },
    { id: ids.live2, title: '数码新品首发，限量秒杀', coverUrl: 'https://picsum.photos/seed/l2/720/1280', anchorId: ids.user2, status: 'offline', currentProductId: null, onlineCount: 0, likeCount: 12000, viewerCount: 5600, createdAt: now },
  ];
  write('live_rooms.json', liveRooms);

  const liveRoomProducts = [
    { id: ids.live1 + '_' + ids.product1, liveRoomId: ids.live1, productId: ids.product1, displayOrder: 0, isExplaining: true },
    { id: ids.live1 + '_' + ids.product7, liveRoomId: ids.live1, productId: ids.product7, displayOrder: 1, isExplaining: false },
    { id: ids.live1 + '_' + ids.product8, liveRoomId: ids.live1, productId: ids.product8, displayOrder: 2, isExplaining: false },
    { id: ids.live2 + '_' + ids.product2, liveRoomId: ids.live2, productId: ids.product2, displayOrder: 0, isExplaining: false },
    { id: ids.live2 + '_' + ids.product4, liveRoomId: ids.live2, productId: ids.product4, displayOrder: 1, isExplaining: false },
  ];
  write('live_room_products.json', liveRoomProducts);

  // Empty collections
  write('carts.json', []);
  write('orders.json', []);
  write('order_items.json', []);
  write('comments.json', []);
  write('likes.json', []);
  write('collections.json', []);
  write('user_coupons.json', []);

  // Coupons
  const coupons = [
    { id: ids.coupon1, title: '新人满99减20', type: 'fixed', value: 20, minPurchase: 99, stock: 500, validFrom: '2026-05-01', validTo: '2026-12-31', createdAt: now },
    { id: ids.coupon2, title: '全场9折', type: 'percentage', value: 10, minPurchase: 0, stock: 300, validFrom: '2026-05-01', validTo: '2026-12-31', createdAt: now },
  ];
  write('coupons.json', coupons);

  console.log('Seed data created successfully!');
  console.log(`Users: 3 (alice/123456, bob/123456, admin/admin123)`);
  console.log(`Products: 10`);
  console.log(`Videos: 5`);
  console.log(`Live rooms: 2`);
  console.log(`Coupons: 2`);
}

run();
