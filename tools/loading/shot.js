// usage: node shot.js out.png [time=2.5] [w=540] [h=1170] [q=1]
// Renders app/.../LoadingShader.kt (AGSL) through WebGL2 by mapping the AGSL type names to GLSL.
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const [,, out, time = '2.5', W = '540', H = '1170', Q = '1'] = process.argv;
const kt = fs.readFileSync(path.join(__dirname, '../../app/src/main/java/com/maze3d/LoadingShader.kt'), 'utf8');
let src = kt.split('const val SRC = """')[1].split('"""')[0];
src = src.replace(/half4 main\(float2 fc\)/, 'vec4 mainImage(vec2 fc)').replace(/half4\(/g, 'vec4(')
  .replace(/float2/g, 'vec2').replace(/float3/g, 'vec3').replace(/float4/g, 'vec4');
const fsrc = '#version 300 es\nprecision highp float;\nout vec4 o;\n' + src + '\nvoid main(){ o = mainImage(vec2(gl_FragCoord.x, uRes.y - gl_FragCoord.y)); }';
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--use-gl=angle'] });
  const p = await b.newPage({ viewport: { width: +W, height: +H } });
  p.on('console', m => { if (m.type() === 'error') console.log('console:', m.text().slice(0, 1200)); });
  await p.setContent('<body style="margin:0"><canvas id=c width=' + W + ' height=' + H + '></canvas>');
  const err = await p.evaluate(([fsrc, time, W, H, Q]) => {
    const gl = document.getElementById('c').getContext('webgl2');
    const mk = (t, s) => { const sh = gl.createShader(t); gl.shaderSource(sh, s); gl.compileShader(sh); if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(sh)); return sh; };
    const pr = gl.createProgram();
    gl.attachShader(pr, mk(gl.VERTEX_SHADER, '#version 300 es\nvoid main(){ vec2 p=vec2((gl_VertexID<<1)&2, gl_VertexID&2); gl_Position=vec4(p*2.0-1.0,0,1);}'));
    gl.attachShader(pr, mk(gl.FRAGMENT_SHADER, fsrc));
    gl.linkProgram(pr); if (!gl.getProgramParameter(pr, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(pr));
    gl.useProgram(pr);
    gl.uniform2f(gl.getUniformLocation(pr, 'uRes'), +W, +H); gl.uniform1f(gl.getUniformLocation(pr, 'uTime'), +time); gl.uniform1f(gl.getUniformLocation(pr, 'uQ'), +Q); const U=n=>gl.getUniformLocation(pr,n); gl.uniform3f(U('uFloorA'),0.48,0.79,0.29); gl.uniform3f(U('uWall'),0.29,0.52,0.2); gl.uniform3f(U('uWallTop'),0.6,0.9,0.4); gl.uniform3f(U('uBallA'),0.95,0.15,0.15); gl.uniform3f(U('uBallB'),1,0.85,0.1); gl.uniform1f(U('uBallKind'),1);
    gl.drawArrays(gl.TRIANGLES, 0, 3);
    return 'ok';
  }, [fsrc, time, W, H, Q]).catch(e => 'ERR ' + e.message);
  console.log(err);
  await p.screenshot({ path: out });
  await b.close();
})();
