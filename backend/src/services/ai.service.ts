import OpenAI from 'openai';

let client: OpenAI | null = null;

function getClient(): OpenAI {
  if (!client) {
    client = new OpenAI({
      apiKey: process.env.AI_API_KEY || 'sk-placeholder',
      baseURL: process.env.AI_BASE_URL || 'https://api.deepseek.com/v1',
    });
  }
  return client;
}

const PROMPTS: Record<string, (ctx: Record<string, string>) => string> = {
  video_title: (ctx) =>
    `你是一个短视频运营专家。根据以下信息，生成5个吸引人的短视频标题（每个标题15字以内），用中文输出，每行一个标题，不要编号：\n- 商品名称：${ctx.productName || '未知'}\n- 商品描述：${ctx.productDescription || '无'}\n- 商品价格：¥${ctx.productPrice || '?'}\n- 商品分类：${ctx.productCategory || '未分类'}\n- 视频内容描述：${ctx.videoContent || '商品展示'}`,

  product_selling_points: (ctx) =>
    `你是一个电商直播选品专家。根据以下商品信息，生成5条商品卖点文案（每条30字以内），突出商品的核心优势和购买理由，用中文输出，每行一条，不要编号：\n- 商品名称：${ctx.productName || '未知'}\n- 商品描述：${ctx.productDescription || '无'}\n- 商品价格：¥${ctx.productPrice || '?'}\n- 商品分类：${ctx.productCategory || '未分类'}`,

  product_description: (ctx) =>
    `你是一个电商文案专家。根据以下商品信息，生成一段80-150字的商品描述，要求吸引人、突出卖点、适合短视频带货场景，用中文输出：\n- 商品名称：${ctx.productName || '未知'}\n- 商品描述：${ctx.productDescription || '无'}\n- 商品价格：¥${ctx.productPrice || '?'}\n- 商品分类：${ctx.productCategory || '未分类'}`,

  live_commentary: (ctx) =>
    `你是一个直播带货主播的助理。根据以下商品信息，生成一段30秒的直播讲解话术（约80-120字），要求口语化、有感染力、包含促销感，用中文输出：\n- 商品名称：${ctx.productName || '未知'}\n- 商品描述：${ctx.productDescription || '无'}\n- 商品价格：¥${ctx.productPrice || '?'}\n- 原价：¥${ctx.originalPrice || '?'}\n- 商品分类：${ctx.productCategory || '未分类'}`,

  product_recommendation: (ctx) =>
    `你是一个短视频带货达人。根据以下商品信息，生成一段吸引人的商品推荐语（60-100字），适合放在视频描述或评论区引导购买，用中文输出：\n- 商品名称：${ctx.productName || '未知'}\n- 商品描述：${ctx.productDescription || '无'}\n- 商品价格：¥${ctx.productPrice || '?'}\n- 商品分类：${ctx.productCategory || '未分类'}`,
};

export interface GenerateRequest {
  type: 'video_title' | 'product_selling_points' | 'product_description' | 'live_commentary' | 'product_recommendation';
  context: Record<string, string>;
}

export interface GenerateResponse {
  text: string;
  type: string;
}

export function generateFallbackText(req: GenerateRequest): GenerateResponse {
  const ctx = req.context || {};
  const productName = String(ctx.productName || '这款商品');
  const description = String(ctx.productDescription || '品质出众');
  const price = String(ctx.productPrice || '?');
  const category = String(ctx.productCategory || '好物');
  const originalPrice = String(ctx.originalPrice || '');

  const templates: Record<string, string> = {
    video_title: [
      `${productName}闭眼入不踩雷`,
      `${category}好物推荐`,
      `${productName}实测真香`,
      `预算内的高质感选择`,
      `今天就看这款${category}`,
    ].join('\n'),
    product_selling_points: [
      `${productName}主打${description}，日常使用和送礼都合适`,
      `到手价约¥${price}，兼顾质感和性价比`,
      `细节设计更贴近真实使用场景，减少选择成本`,
      `适合想要快速提升体验的用户，实用不花哨`,
      `库存有限时更适合尽早下单，避免错过优惠`,
    ].join('\n'),
    product_description:
      `${productName}是一款适合短视频带货场景推荐的${category}商品，特点是${description}。当前价格约¥${price}，适合追求实用、质感和性价比的用户。`,
    live_commentary:
      `来看这款${productName}，它的核心优势是${description}。现在直播间价格约¥${price}${originalPrice ? `，原价¥${originalPrice}` : ''}，适合想要入手${category}的朋友，喜欢的话可以先加购再慢慢看细节。`,
    product_recommendation:
      `${productName}真的很适合近期入手，${description}，日常搭配和使用都不挑场景。现在价格约¥${price}，属于${category}里比较有性价比的一款，喜欢实用好物的可以重点看看。`,
  };

  return {
    text: templates[req.type] || `${productName}主打${description}，当前价格约¥${price}，适合正在挑选${category}的用户参考。`,
    type: req.type,
  };
}

export async function generateText(req: GenerateRequest): Promise<GenerateResponse> {
  const promptFn = PROMPTS[req.type];
  if (!promptFn) {
    throw new Error(`Unsupported generation type: ${req.type}`);
  }

  if (!isConfigured()) {
    return generateFallbackText(req);
  }

  const userMessage = promptFn(req.context);
  const model = process.env.AI_MODEL || 'deepseek-chat';

  const openai = getClient();
  const completion = await openai.chat.completions.create({
    model,
    messages: [
      { role: 'system', content: '你是一个专业的电商内容创作助手，所有输出必须是中文。' },
      { role: 'user', content: userMessage },
    ],
    temperature: 0.8,
    max_tokens: 600,
  });

  const text = completion.choices[0]?.message?.content?.trim() || '';
  return { text, type: req.type };
}

export function isConfigured(): boolean {
  const apiKey = process.env.AI_API_KEY;
  return !!(apiKey && apiKey !== 'sk-placeholder');
}
