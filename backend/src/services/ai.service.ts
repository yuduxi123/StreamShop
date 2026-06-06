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

export async function generateText(req: GenerateRequest): Promise<GenerateResponse> {
  const promptFn = PROMPTS[req.type];
  if (!promptFn) {
    throw new Error(`Unsupported generation type: ${req.type}`);
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
