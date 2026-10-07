/** 本机真实云端联调：请求经 Vite 同源代理到 Java 服务，密钥不进入浏览器。 */
export { listImageWorkflows, createImageTask } from './fixture-api';
type Query = Record<string, string | number | undefined>;
async function request(path: string, method='GET', body?: unknown, query?: Query) {
  const params=new URLSearchParams();Object.entries(query ?? {}).forEach(([k,v])=>{if(v!==undefined && v!=='')params.set(k,String(v));});
  const response=await fetch('/local-cloud'+path+(params.size?'?'+params:''),{method,headers:body instanceof Blob?{'Content-Type':body.type || 'application/octet-stream'}:body===undefined?{}:{'Content-Type':'application/json'},body:body instanceof Blob?body:body===undefined?undefined:JSON.stringify(body)});
  if(!response.ok) {let message='本机服务请求失败';try{message=(await response.json()).msg || message;}catch{}throw new Error(message);}
  return {data:await response.json()};
}
async function media(id: string | number, suffix: string) {
  const response=await fetch(`/local-cloud/image/assets/${id}/${suffix}`);
  if(!response.ok)throw new Error('读取本机素材失败');const blob=await response.blob();if(!blob.size || !blob.type.startsWith('image/'))throw new Error('素材内容无效');return URL.createObjectURL(blob);
}
export const listCloudImageModels=()=>request('/image/cloud/models');
export const checkCloudImageModels=()=>request('/image/cloud/check');
export const createCloudImageTask=(body: unknown)=>request('/image/cloud/tasks','POST',body);
export const executeImageTask=(id: string | number)=>request(`/image/tasks/${id}/execute`,'POST');
export const retryImageTask=(id: string | number)=>request(`/image/tasks/${id}/retry`,'POST');
export const cancelImageTask=(id: string | number)=>request(`/image/tasks/${id}/cancel`,'POST');
export const listImageTasks=(query?: Query)=>request('/image/tasks','GET',undefined,query);
export const getImageTask=(id: string | number)=>request(`/image/tasks/${id}`);
export const listImageAssets=(query?: Query)=>request('/image/assets','GET',undefined,query);
export const deleteImageAsset=(id: string | number)=>request(`/image/assets/${id}`,'DELETE');
export const uploadImageAsset=async(file: File, onProgress?: (percent: number)=>void)=>{onProgress?.(0);const result=await request('/image/assets','POST',file,{name:file.name});onProgress?.(100);return result;};
export const fetchImageAssetBlobUrl=(id: string | number)=>media(id,'content');
export const fetchImageAssetThumbnailBlobUrl=(id: string | number)=>media(id,'thumbnail');
