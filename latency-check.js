const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: false }); // headless: false to see the browser
  const page = await browser.newPage();

  const start = Date.now();
  await page.goto('https://dev-demo.neuron7.ai/#/account/login', { waitUntil: 'networkidle' });
  const end = Date.now();

  const timing = await page.evaluate(() => JSON.stringify(window.performance.timing));
  const perfTiming = JSON.parse(timing);

  console.log("📊 Website Latency Metrics:");
  console.log(`🕒 Total Page Load Time: ${end - start} ms`);
  console.log(`⏱️ Time to First Byte (TTFB): ${perfTiming.responseStart - perfTiming.requestStart} ms`);
  console.log(`📄 DOM Content Loaded: ${perfTiming.domContentLoadedEventEnd - perfTiming.navigationStart} ms`);
  console.log(`📦 Full Load Event: ${perfTiming.loadEventEnd - perfTiming.navigationStart} ms`);

  await browser.close();
})();
