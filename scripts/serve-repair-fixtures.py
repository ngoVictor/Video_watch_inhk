"""Local finite synthetic media fixtures for RepairInstrumentation. No real TV content."""
import argparse,json,pathlib,subprocess,threading
from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
from urllib.parse import urlparse
ROOT=pathlib.Path(__file__).resolve().parents[1]

def main():
 p=argparse.ArgumentParser();p.add_argument('--port',type=int,default=18765);p.add_argument('--directory',type=pathlib.Path,default=ROOT/'artifacts/repair/media');p.add_argument('--live',action='store_true',help='Generate a sliding HLS window from the local synthetic sample');args=p.parse_args();directory=args.directory.resolve();directory.mkdir(parents=True,exist_ok=True)
 commands=[['-f','lavfi','-i','testsrc=size=320x180:rate=25','-f','lavfi','-i','sine=frequency=440:sample_rate=44100','-t','8','-pix_fmt','yuv420p','-c:v','libx264','-threads','2','-g','50','-c:a','aac','-movflags','+faststart','sample.mp4'],['-i','sample.mp4','-c','copy','-hls_time','2','-hls_list_size','0','-hls_segment_filename','chunk%03d.ts','vod.m3u8'],['-i','sample.mp4','-c','copy','-f','dash','sample.mpd']]
 if not (directory/'sample.mpd').exists():
  for command in commands:subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y',*command],cwd=directory,check=True,timeout=45)
 if not (directory/'hd-mp2.ts').exists():
  subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','testsrc2=size=1920x1080:rate=25',
    '-f','lavfi','-i','sine=frequency=880:sample_rate=48000','-t','25','-c:v','libx264','-preset','ultrafast','-threads','2',
    '-pix_fmt','yuv420p','-b:v','5M','-c:a','mp2','-b:a','192k','-f','mpegts','hd-mp2.ts'],cwd=directory,check=True,timeout=60)
 if not (directory/'hd-silent.ts').exists():
  subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i','hd-mp2.ts','-map','0:v:0','-c','copy','hd-silent.ts'],cwd=directory,check=True,timeout=30)
 rows=[];lock=threading.Lock()
 class Handler(SimpleHTTPRequestHandler):
  def __init__(self,*a,**kw):super().__init__(*a,directory=str(directory),**kw)
  def do_GET(self):
   path=urlparse(self.path).path
   if path=='/requests':
    with lock:data=json.dumps(rows).encode()
    self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data);return
   with lock:
    rows.append(dict(path=self.path,cache=self.headers.get('Cache-Control',''),authorization=self.headers.get('Authorization','')))
    (directory.parent/'media-requests.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
   if path=='/redirect':self.send_response(302);self.send_header('Location','/master');self.end_headers();return
   if path in ('/master','/playlist.m3u8','/playlist.m3u'):
    data=b'#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=320x180\n/variant\n'
   elif path=='/variant':data=(directory/'vod.m3u8').read_bytes().replace(b'#EXT-X-ENDLIST\n',b'').replace(b'#EXT-X-PLAYLIST-TYPE:VOD\n',b'')
   else:return super().do_GET()
   self.send_response(200);self.send_header('Content-Type','application/vnd.apple.mpegurl');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
  def log_message(self,*args):pass
 server=ThreadingHTTPServer(('127.0.0.1',args.port),Handler);producer=None
 try:
  if args.live:
   producer=subprocess.Popen(['ffmpeg','-hide_banner','-nostdin','-loglevel','error','-y','-re','-stream_loop','-1',
     '-i','sample.mp4','-c','copy','-hls_time','2','-hls_list_size','5','-hls_flags','delete_segments+omit_endlist+independent_segments',
     '-hls_segment_filename','live-%05d.ts','live.m3u8'],cwd=directory,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  print('Local synthetic media server on 127.0.0.1:'+str(args.port),flush=True)
  server.serve_forever()
 finally:
  server.server_close()
  if producer is not None:
   producer.terminate()
   try:producer.wait(timeout=5)
   except subprocess.TimeoutExpired:producer.kill();producer.wait()
if __name__=='__main__':main()
