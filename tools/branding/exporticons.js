const http=require('http'),fs=require('fs'),path=require('path');const {chromium}=require('playwright');
const out=process.argv[2];const root=__dirname;
const srv=http.createServer((q,r)=>{fs.readFile(path.join(root,'appicon.html'),(e,d)=>{r.writeHead(200,{'Content-Type':'text/html'});r.end(d)})}).listen(0,async()=>{
 const b=await chromium.launch();const p=await b.newPage();await p.goto(`http://localhost:${srv.address().port}/`);await p.waitForFunction(()=>document.title==='ready');
 const save=async(rel,mode,size,round)=>{let url=await p.evaluate(([m,s])=>window.render(m,s),[mode,size]);
   if(round){url=await p.evaluate(async([u,s])=>{const i=new Image();await new Promise(r=>{i.onload=r;i.src=u});const c=document.createElement('canvas');c.width=c.height=s;const g=c.getContext('2d');g.beginPath();g.arc(s/2,s/2,s/2,0,7);g.clip();g.drawImage(i,0,0);return c.toDataURL('image/png')},[url,size])}
   const f=path.join(out,rel);fs.mkdirSync(path.dirname(f),{recursive:true});fs.writeFileSync(f,Buffer.from(url.split(',')[1],'base64'))};
 const dens={mdpi:1,hdpi:1.5,xhdpi:2,xxhdpi:3,xxxhdpi:4};
 for(const [d,k] of Object.entries(dens)){
   await save(`res/mipmap-${d}/ic_launcher_foreground.png`,'fg',Math.round(108*k));
   await save(`res/mipmap-${d}/ic_launcher_monochrome.png`,'mono',Math.round(108*k));
   await save(`res/mipmap-${d}/ic_launcher.png`,'full',Math.round(48*k));
   await save(`res/mipmap-${d}/ic_launcher_round.png`,'full',Math.round(48*k),true);
 }
 await save('playstore/rollabyrinth_icon_512.png','full',512);
 await b.close();srv.close();});
