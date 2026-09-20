/**
 * Local WebGL flower scene. No network requests, model files or external libraries.
 * The geometry and lighting come from the approved design.
 * Call destroy() when the login route unmounts.
 */
export function createFloralScene(root, canvas, options = {}) {
  const media = window.matchMedia('(prefers-reduced-motion: reduce)');
  const state = { playing: options.playing ?? !media.matches, palette: 'pearl', bloom: 1 };
  const buffers = [], shaders = [], cleanups = [];
  let gl = null, program = null, resizeObserver = null;
  let disposed = false, failed = false, raf = 0, redraw = () => {};
  const controller = {
    available: false,
    get playing() { return state.playing; },
    setPlaying(value) {
      state.playing = Boolean(value);
      if (disposed || failed) return;
      cancelAnimationFrame(raf); raf = 0;
      redraw();
    },
    destroy() {
      if (disposed) return;
      disposed = true;
      cancelAnimationFrame(raf);
      resizeObserver?.disconnect();
      cleanups.splice(0).forEach(fn => fn());
      if (gl && !gl.isContextLost()) {
        buffers.forEach(b => gl.deleteBuffer(b));
        if (program) gl.deleteProgram(program);
        shaders.forEach(s => gl.deleteShader(s));
      }
    }
  };
  function listen(target, event, handler, opts) {
    target.addEventListener(event, handler, opts);
    cleanups.push(() => target.removeEventListener(event, handler, opts));
  }
  function fallback() {
    failed = true; controller.available = false;
    cancelAnimationFrame(raf); raf = 0;
    canvas.style.visibility = 'hidden';
    options.onFallback?.();
  }
  try {
    gl = canvas.getContext('webgl', { alpha:true, antialias:true, premultipliedAlpha:false });
    if (!gl) { fallback(); return controller; }
    const vert=`attribute vec3 aPos;attribute vec3 aNormal;uniform mat4 uModel;uniform vec2 uViewport;varying vec3 vNormal;varying vec3 vPos;varying vec3 vLocal;void main(){vec4 p=uModel*vec4(aPos,1.0);vNormal=mat3(uModel)*aNormal;vPos=p.xyz;vLocal=aPos;gl_Position=vec4(p.x/uViewport.x,p.y/uViewport.y,-p.z/30.0,1.0);}`;
    const frag=`precision highp float;varying vec3 vNormal;varying vec3 vPos;varying vec3 vLocal;uniform vec3 uColor;void main(){vec3 n=normalize(vNormal);if(!gl_FrontFacing)n=-n;vec3 view=vec3(0.,0.,1.);vec3 light=normalize(vec3(-.65,1.,1.6));vec3 fill=normalize(vec3(.8,-.2,.6));float diffuse=max(dot(n,light),0.);float back=max(dot(n,fill),0.);float ao=.73+.27*smoothstep(.0,2.5,length(vLocal.xy));vec3 base=uColor*(.52+.48*diffuse)*ao+vec3(.35,.43,.59)*back*.26;float spec=pow(max(dot(n,normalize(light+view)),0.),42.);float sheen=pow(max(dot(n,normalize(vec3(-1.,.6,1.)+view)),0.),9.);float fres=pow(1.-abs(dot(n,view)),2.8);base+=vec3(1.,.95,.87)*spec*.25+vec3(.87,.88,1.)*sheen*.14;vec3 pearl=mix(vec3(.55,.70,.91),vec3(.92,.69,.79),.5+.5*sin(n.x*3.+n.y*2.));base+=pearl*fres*.19;float grain=fract(sin(dot(gl_FragCoord.xy,vec2(12.9898,78.233)))*43758.5453)-.5;base+=grain*.007;gl_FragColor=vec4(pow(base,vec3(.86)),1.);}`;
    function shader(type,src){const s=gl.createShader(type);if(!s)throw Error("Unable to allocate shader");shaders.push(s);gl.shaderSource(s,src);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
    program=gl.createProgram();gl.attachShader(program,shader(gl.VERTEX_SHADER,vert));gl.attachShader(program,shader(gl.FRAGMENT_SHADER,frag));gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));gl.useProgram(program);
    const points=[],normals=[];
    function petal(t,v,L,W,angle,zbase,ring){const envelope=Math.pow(Math.max(.00001,Math.sin(Math.PI*t)),.96);const tt=(1-Math.cos(Math.PI*t))*.5;const x=.10+L*tt;const y=W*v*envelope;const z=zbase+(.65+ring*.12)*Math.pow(tt,2)*2.1+.60*v*v*envelope+.06*Math.sin(tt*6+v*3)*envelope+.10*Math.sin(v*2)*tt;const a=angle+.16*t;return [x*Math.cos(a)-y*Math.sin(a),x*Math.sin(a)+y*Math.cos(a),z];}
    function sample(t,v,L,W,a,z,r){const p=petal(t,v,L,W,a,z,r),pa=petal(Math.min(.9999,t+.0001),v,L,W,a,z,r),pb=petal(t,v+.0001,L,W,a,z,r);const u=pa.map((x,i)=>x-p[i]),w=pb.map((x,i)=>x-p[i]);let n=[u[1]*w[2]-u[2]*w[1],u[2]*w[0]-u[0]*w[2],u[0]*w[1]-u[1]*w[0]];const size=Math.hypot(...n)||1;n=n.map(x=>x/size);return {p,n};}
    const rings=[{n:8,l:3.50,w:1.53,z:0},{n:7,l:2.74,w:1.23,z:.15},{n:6,l:1.98,w:.99,z:.30},{n:5,l:1.31,w:.72,z:.43},{n:3,l:.75,w:.44,z:.54}];
    rings.forEach((ring,r)=>{for(let k=0;k<ring.n;k++){const a=k*Math.PI*2/ring.n+r*.65;const grid=[];const rows=38,cols=24;for(let i=0;i<=rows;i++){grid[i]=[];for(let j=0;j<=cols;j++)grid[i][j]=sample(.0001+i/rows*.9997,-1+j/cols*2,ring.l,ring.w,a,ring.z,r);}for(let i=0;i<rows;i++)for(let j=0;j<cols;j++){for(const p of [grid[i][j],grid[i+1][j],grid[i][j+1],grid[i+1][j],grid[i+1][j+1],grid[i][j+1]]){points.push(...p.p);normals.push(...p.n);}}}});
    function buffer(name,data){const b=gl.createBuffer();if(!b)throw Error("Unable to allocate buffer");buffers.push(b);gl.bindBuffer(gl.ARRAY_BUFFER,b);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(data),gl.STATIC_DRAW);const a=gl.getAttribLocation(program,name);gl.enableVertexAttribArray(a);gl.vertexAttribPointer(a,3,gl.FLOAT,false,0,0);}
    buffer('aPos',points);buffer('aNormal',normals);
    const modelLoc=gl.getUniformLocation(program,'uModel'),viewLoc=gl.getUniformLocation(program,'uViewport'),colorLoc=gl.getUniformLocation(program,'uColor');
    gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);gl.clearColor(0,0,0,0);
    function mult(a,b){const out=new Float32Array(16);for(let c=0;c<4;c++)for(let r=0;r<4;r++)for(let k=0;k<4;k++)out[c*4+r]+=a[k*4+r]*b[c*4+k];return out;}
    function model(x,y,z,s,rx,ry,rz){const cx=Math.cos(rx),sx=Math.sin(rx),cy=Math.cos(ry),sy=Math.sin(ry),cz=Math.cos(rz),sz=Math.sin(rz);const X=[1,0,0,0,0,cx,sx,0,0,-sx,cx,0,0,0,0,1],Y=[cy,0,-sy,0,0,1,0,0,sy,0,cy,0,0,0,0,1],Z=[cz,sz,0,0,-sz,cz,0,0,0,0,1,0,0,0,0,1];const m=mult(Z,mult(Y,X));for(let i=0;i<12;i++)m[i]*=s;m[12]=x;m[13]=y;m[14]=z;return m;}

    let width=1,height=1,aspect=1;
    let targetX=0,targetY=0,mx=0,my=0,time=0,last=0,lastPaint=0;
    function paint(now) {
      raf=0;
      if(disposed || failed || document.hidden) return;
      if(state.playing && lastPaint && now-lastPaint < 1000/30) {
        raf=requestAnimationFrame(paint); return;
      }
      const dt=last ? Math.min((now-last)/1000,.05) : 0;
      last=now;lastPaint=now;
      if(state.playing){time+=dt;mx+=(targetX-mx)*.025;my+=(targetY-my)*.025;}
      gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
      const shift=Math.sin(time*.23)*.08;const colors=state.palette==='lilac'?[.88,.82,.94]:state.palette==='blue'?[.81,.90,.99]:[.99,.955,.93];gl.uniform3fv(colorLoc,colors);gl.uniformMatrix4fv(modelLoc,false,model(-5.5*aspect*.78,-1.48+shift,0,1.18*state.bloom,-.40+my*.07,-.35+mx*.10,-.16+Math.sin(time*.16)*.022));gl.drawArrays(gl.TRIANGLES,0,points.length/3);gl.uniform3fv(colorLoc,state.palette==='blue'?[.85,.92,1.]:[.88,.86,.95]);gl.uniformMatrix4fv(modelLoc,false,model(5.5*aspect*.85,3.23-shift,-1,.88*state.bloom,.32-my*.06,.48+mx*.06,.85-Math.sin(time*.18)*.03));gl.drawArrays(gl.TRIANGLES,0,points.length/3);
      if(state.playing) raf=requestAnimationFrame(paint);
    }
    redraw=()=>{
      if(disposed || failed || document.hidden) return;
      last=0;lastPaint=0;
      cancelAnimationFrame(raf);raf=requestAnimationFrame(paint);
    };
    function resize(){
      width=Math.max(1,root.clientWidth);height=Math.max(1,root.clientHeight);
      const d=Math.min(window.devicePixelRatio||1,1.6);
      canvas.width=Math.round(width*d);canvas.height=Math.round(height*d);
      aspect=width/height;gl.viewport(0,0,canvas.width,canvas.height);
      gl.uniform2f(viewLoc,5.5*aspect,5.5);redraw();
    }
    if(window.ResizeObserver){resizeObserver=new ResizeObserver(resize);resizeObserver.observe(root);}
    else listen(window,'resize',resize);
    listen(root,'pointermove',event=>{const r=root.getBoundingClientRect();targetX=(event.clientX-r.left)/r.width-.5;targetY=(event.clientY-r.top)/r.height-.5;},{passive:true});
    listen(root,'pointerleave',()=>{targetX=0;targetY=0;});
    listen(document,'visibilitychange',()=>{cancelAnimationFrame(raf);raf=0;if(!document.hidden)redraw();});
    listen(canvas,'webglcontextlost',event=>{event.preventDefault();fallback();});
    controller.available=true;
    canvas.style.visibility='visible';
    resize();
  } catch(error) {
    fallback();
    controller.destroy();
    options.onError?.(error);
  }
  return controller;
}
