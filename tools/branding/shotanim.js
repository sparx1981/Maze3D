const http=require('http'),fs=require('fs'),path=require('path');const {chromium}=require('playwright');
const root=__dirname;const srv=http.createServer((q,r)=>{const f=path.join(root,q.url.split('?')[0]==='/'?'loading.html':q.url.split('?')[0]);fs.readFile(f,(e,d)=>{if(e){r.writeHead(404);r.end()}else{r.writeHead(200,{'Content-Type':'text/html'});r.end(d)}})}).listen(0,async()=>{
 const b=await chromium.launch();const p=await b.newPage({viewport:{width:540,height:960}});
 for(const m of ['A','B','C']){
  await p.goto(`http://localhost:${srv.address().port}/?m=${m}`);await p.waitForFunction(()=>document.title==='ready');
  fs.mkdirSync(`/tmp/anim_${m}`,{recursive:true});
  const FPS=30,DUR=3.2;
  for(let i=0;i<FPS*DUR;i++){await p.evaluate(t=>window.draw(t),i/FPS);await p.screenshot({path:`/tmp/anim_${m}/f${String(i).padStart(3,'0')}.png`})}
  console.log('frames',m);
 }
 await b.close();srv.close();});
