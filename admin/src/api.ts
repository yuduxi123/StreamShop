const BASE_URL = 'http://localhost:3000/api';

let authToken: string | null = null;

async function request(path: string, options: RequestInit = {}) {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  };
  if (authToken) headers['Authorization'] = 'Bearer ' + authToken;

  const res = await fetch(BASE_URL + path, { ...options, headers });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(body || `HTTP ${res.status}`);
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

export const api = {
  // Auth
  login: (username: string, password: string) =>
    request('/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) })
      .then(data => { authToken = data.token; return data; }),

  // Videos
  getVideos: (page = 1, limit = 50) => request(`/videos?page=${page}&limit=${limit}`),
  getVideo: (id: string) => request(`/videos/${id}`),
  createVideo: (data: any) => request('/videos', { method: 'POST', body: JSON.stringify(data) }),
  updateVideo: (id: string, data: any) => request(`/videos/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
  deleteVideo: (id: string) => request(`/videos/${id}`, { method: 'DELETE' }),

  // Products
  getProducts: (page = 1, limit = 50, category?: string) => {
    let url = `/products?page=${page}&limit=${limit}`;
    if (category) url += `&category=${category}`;
    return request(url);
  },
  getProduct: (id: string) => request(`/products/${id}`),
  createProduct: (data: any) => request('/products', { method: 'POST', body: JSON.stringify(data) }),
  updateProduct: (id: string, data: any) => request(`/products/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
  deleteProduct: (id: string) => request(`/products/${id}`, { method: 'DELETE' }),
  bindVideo: (id: string, videoId: string) =>
    request(`/products/${id}/bind-video`, { method: 'POST', body: JSON.stringify({ videoId }) }),

  // Live Rooms
  getRooms: (page = 1, limit = 50) => request(`/live/rooms?page=${page}&limit=${limit}`),
  getRoom: (id: string) => request(`/live/rooms/${id}`),
  createRoom: (data: any) => request('/live/rooms', { method: 'POST', body: JSON.stringify(data) }),
  updateRoom: (id: string, data: any) => request(`/live/rooms/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
  startRoom: (id: string) => request(`/live/rooms/${id}/start`, { method: 'POST' }),
  endRoom: (id: string) => request(`/live/rooms/${id}/end`, { method: 'POST' }),
  addRoomProduct: (id: string, productId: string) =>
    request(`/live/rooms/${id}/products`, { method: 'POST', body: JSON.stringify({ productId }) }),
  explainProduct: (roomId: string, productId: string) =>
    request(`/live/rooms/${roomId}/product/${productId}/explain`, { method: 'POST' }),

  // Orders
  getOrders: (page = 1, limit = 50, status?: string) => {
    let url = `/orders?page=${page}&limit=${limit}`;
    if (status && status !== 'all') url += `&status=${status}`;
    return request(url);
  },
  getOrder: (id: string) => request(`/orders/${id}`),
  payOrder: (id: string) => request(`/orders/${id}/pay`, { method: 'POST', body: '{}' }),
  cancelOrder: (id: string) => request(`/orders/${id}/cancel`, { method: 'PATCH', body: '{}' }),

  // Auth status
  isAuthenticated: () => !!authToken,
  logout: () => { authToken = null; },
  getToken: () => authToken,
};
