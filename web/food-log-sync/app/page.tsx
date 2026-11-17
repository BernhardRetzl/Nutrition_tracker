import Tracker from './tracker';
import { requireChatGPTUser } from './chatgpt-auth';
export const dynamic='force-dynamic';
export default async function Page(){const user=await requireChatGPTUser('/');return <Tracker owner={user.userId}/>;}
