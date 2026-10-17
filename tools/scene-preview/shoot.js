const { chromium } = require('playwright');
const fs=require('fs');
(async()=>{
  fs.mkdirSync(process.argv[3],{recursive:true});
  const data=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
  const browser=await chromium.launch({args:['--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist']});
  for(const name of Object.keys(data.frames)){
    const page=await browser.newPage({viewport:{width:1200,height:675}});
    page.on('console',m=>console.log('console',m.text()));
    await page.goto('file://'+__dirname+'/replay.html');
    await page.evaluate(d=>{window.DATA=d},data);
    try{ const err=await page.evaluate(n=>run(n),name); console.log(name,'glError',err);}catch(e){console.log(name,'FAIL',e.message.slice(0,800));}
    await page.locator('#c').screenshot({path:`${process.argv[3]}/${name}.png`});
    await page.close();
  }
  await browser.close();
})();
