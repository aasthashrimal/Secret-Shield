package com.secretshield.security;
import java.io.*;import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;import org.springframework.core.annotation.Order;import org.springframework.http.MediaType;import org.springframework.stereotype.Component;import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;import jakarta.servlet.http.*;
@Component @Order(Ordered.HIGHEST_PRECEDENCE) public class MachinePayloadLimitFilter extends OncePerRequestFilter {
 public static final int LIMIT=1024*1024;
 @Override protected boolean shouldNotFilter(HttpServletRequest r){return !("POST".equals(r.getMethod())&&r.getRequestURI().matches(".*/api/ci/repositories/[^/]+/scan-results"));}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws IOException,ServletException{
  if(req.getContentLengthLong()>LIMIT){tooLarge(res);return;}ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int total=0,n;try(var in=req.getInputStream()){while((n=in.read(buf))!=-1){total+=n;if(total>LIMIT){tooLarge(res);return;}out.write(buf,0,n);}}byte[] body=out.toByteArray();chain.doFilter(new HttpServletRequestWrapper(req){@Override public ServletInputStream getInputStream(){var input=new ByteArrayInputStream(body);return new ServletInputStream(){public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener l){}public int read(){return input.read();}};}@Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),req.getCharacterEncoding()==null?StandardCharsets.UTF_8:java.nio.charset.Charset.forName(req.getCharacterEncoding())));}@Override public int getContentLength(){return body.length;}@Override public long getContentLengthLong(){return body.length;}},res);}
 private static void tooLarge(HttpServletResponse r)throws IOException{r.setStatus(413);r.setContentType(MediaType.APPLICATION_JSON_VALUE);r.getWriter().write("{\"success\":false,\"status\":413,\"code\":\"PAYLOAD_TOO_LARGE\",\"message\":\"Request payload too large\"}");}
}
