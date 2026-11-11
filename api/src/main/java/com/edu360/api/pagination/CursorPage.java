package com.edu360.api.pagination;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import com.edu360.api.exception.ApiException;
/** Shared opaque cursor and query validation. SQL callers must use the allow-listed sort field. */
public final class CursorPage {
 public static final int DEFAULT_LIMIT=50,MAX_LIMIT=200;
 private CursorPage(){}
 public record Cursor(String sortField,String sortValue,UUID id){}
 public record Request(int limit,String sortField,String direction,Cursor cursor){
  public String orderBy(String idColumn){return sortField+" "+direction+", "+idColumn+" "+direction;}
 }
 public static Request request(Integer requestedLimit,String token,String sortField,String direction,Set<String> allowed){
  int limit=requestedLimit==null?DEFAULT_LIMIT:requestedLimit;
  if(limit<1||limit>MAX_LIMIT)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_QUERY","limit must be between 1 and "+MAX_LIMIT+".");
  if(allowed==null||allowed.isEmpty())throw new IllegalArgumentException("At least one allowed sort field is required.");
  String sort=sortField==null||sortField.isBlank()?allowed.stream().sorted().findFirst().orElseThrow():sortField;
  if(!allowed.contains(sort))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_QUERY","Unknown sort field: "+sort);
  String dir=direction==null?"ASC":direction.toUpperCase(java.util.Locale.ROOT);
  if(!dir.equals("ASC")&&!dir.equals("DESC"))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_QUERY","sort direction must be ASC or DESC.");
  return new Request(limit,sort,dir,token==null||token.isBlank()?null:decode(token,sort));
 }
 public static String encode(String field,String value,UUID id){
  if(field==null||value==null||id==null)throw new IllegalArgumentException("Cursor requires sort field, value and id.");
  return Base64.getUrlEncoder().withoutPadding().encodeToString(("v1|"+field+"|"+value+"|"+id).getBytes(StandardCharsets.UTF_8));
 }
 public static Cursor decode(String token,String expected){
  try{String raw=new String(Base64.getUrlDecoder().decode(token),StandardCharsets.UTF_8);String[] p=raw.split("\\|",4);
   if(p.length!=4||!p[0].equals("v1")||!expected.equals(p[1]))throw new IllegalArgumentException();
   return new Cursor(p[1],p[2],UUID.fromString(p[3]));
  }catch(RuntimeException ex){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CURSOR","The pagination cursor is invalid.");}
 }
}
