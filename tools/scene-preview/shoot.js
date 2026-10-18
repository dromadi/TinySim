const { chromium } = require('playwright');
const fs=require('fs');
(async()=>{
  fs.mkdirSync(process.argv[3],{recursive:true});
  const data=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
  const browser=await chromium.launch({...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{}),args:['--allow-file-access-from-files','--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist']});
  const results=[];
  const jpeg=process.env.PREVIEW_FORMAT==='jpeg';
  try {
    for(const name of Object.keys(data.frames)) {
      const page=await browser.newPage({viewport:{width:1200,height:675}});
      page.setDefaultTimeout(90000);
      page.on('console',m=>console.log('console',m.text()));
      try {
        await page.goto('file://'+__dirname+'/replay.html');
        // Each replay context needs one fixture, not the entire recording of every scene.
        const sceneData={init:data.init,frames:{[name]:data.frames[name]},hud:{[name]:data.hud?.[name]||[]}};
        await page.evaluate(d=>{window.DATA=d},sceneData);
        const error=await page.evaluate(n=>run(n),name);
        if(error!==0)throw new Error(`GL error ${error}`);
        await page.locator('#scene').screenshot({path:`${process.argv[3]}/${name}.${jpeg?'jpg':'png'}`,...(jpeg?{type:'jpeg',quality:90}:{})});
        const draws=data.frames[name].filter(op=>op[0]==='drawArrays');
        results.push({name,status:'PASS',glError:error,drawCalls:draws.length,triangles:draws.reduce((sum,op)=>sum+op[2]/3,0)});
        console.log(name,'PASS',draws.length,'draw calls');
      } catch(e) {
        results.push({name,status:'FAIL',error:e.message});console.error(name,'FAIL',e.message);
      } finally { await page.close(); }
    }
  } finally { await browser.close(); }
  fs.writeFileSync(`${process.argv[3]}/results.json`,JSON.stringify(results,null,2));
  if(results.some(r=>r.status!=='PASS'))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1});
