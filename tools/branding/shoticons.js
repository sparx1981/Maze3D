const http=require('http'),fs=require('fs'),path=require('path');const {chromium}=require('playwright');
const root=__dirname;const extra={'/ic_sphere.png':'/tmp/ic_sphere.png','/ic_torus.png':'/tmp/ic_torus.png'};
const srv=http.createServer((q,r)=>{const f=extra[q.url]||path.join(root,q.url==='/'?'icons.html':q.url);fs.readFile(f,(e,d)=>{if(e){r.writeHead(404);r.end()}else{r.writeHead(200,{'Content-Type':f.endsWith('.png')?'image/png':'text/html'});r.end(d)}})}).listen(0,async()=>{
 const b=await chromium.launch();const p=await b.newPage({viewport:{width:1900,height:760}});
 await p.goto(`http://localhost:${srv.address().port}/`);await p.waitForFunction(()=>document.title==='ready');
 await p.screenshot({path:'/tmp/icons_sheet.png'});
 for(const id of ['A','B','C']){await (await p.$('#'+id)).screenshot({path:`/tmp/icon_${id}.png`,omitBackground:true})}
 await b.close();srv.close();});
