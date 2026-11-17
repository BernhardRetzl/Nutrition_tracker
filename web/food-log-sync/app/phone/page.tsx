import { requireChatGPTUser } from '../chatgpt-auth';
export const dynamic='force-dynamic';
export default async function Phone(){await requireChatGPTUser('/phone');return <main style={{fontFamily:'sans-serif',padding:24}}><h1>Phone connected</h1><p>Return to Food Log to sync your foods, recipes and history.</p><a href="/">Open browser log</a></main>;}
