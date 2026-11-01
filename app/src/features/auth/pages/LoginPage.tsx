import { type FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { login } from "../../../services/authManager";
export default function LoginPage() {
 const navigate=useNavigate();
 const [identifier,setIdentifier]=useState(""); const [password,setPassword]=useState("");
 const [showPassword,setShowPassword]=useState(false); const [keepSignedIn,setKeepSignedIn]=useState(false);
 const [error,setError]=useState(""); const [notice,setNotice]=useState(""); const [busy,setBusy]=useState(false);
 const submit=async(e:FormEvent<HTMLFormElement>)=>{e.preventDefault();setError("");setNotice("");
  if(!identifier.trim()||!password){setError("Enter your school email or student ID and password.");return;}
  setBusy(true);try{await login(identifier.trim(),password);navigate("/dashboard");}
  catch(err){setError(err instanceof Error?err.message:"Sign-in failed. Check your details and try again.");}finally{setBusy(false);}
 };
 return <main className="auth-screen">
  <div className="auth-brand"><span className="auth-brand__mark">e</span><span>EDU <strong>360</strong></span></div>
  <section className="auth-card" aria-labelledby="login-title">
   <p className="eds-kicker">WELCOME BACK</p><h1 id="login-title" className="auth-card__title">Welcome back.</h1>
   <p className="auth-card__subtitle">Sign in with your school account to continue to your learning workspace.</p>
   {error&&<div className="auth-message auth-message--error" role="alert">{error}</div>}
   {notice&&<div className="auth-message" role="status">{notice}</div>}
   <form onSubmit={submit} noValidate>
    <div className="eds-field"><label htmlFor="login-identifier">School email or student ID <span>Required</span></label>
     <input id="login-identifier" autoComplete="username" autoCapitalize="none" value={identifier} onChange={e=>setIdentifier(e.target.value)} placeholder="name@school.edu.in or student ID" required /></div>
    <div className="eds-field"><label htmlFor="login-password">Password <span>Required</span></label><div className="auth-password">
     <input id="login-password" autoComplete="current-password" type={showPassword?"text":"password"} value={password} onChange={e=>setPassword(e.target.value)} placeholder="Enter your password" required />
     <button className="auth-password__toggle" type="button" onClick={()=>setShowPassword(v=>!v)} aria-pressed={showPassword} aria-controls="login-password">{showPassword?"Hide":"Show"}</button></div></div>
    <div className="auth-options"><label className="eds-check"><input type="checkbox" checked={keepSignedIn} onChange={e=>setKeepSignedIn(e.target.checked)}/><span>Keep me signed in</span></label>
     <button className="eds-link-button" type="button" onClick={()=>setNotice("Password recovery is managed by your institution. Please contact your school administrator.")}>Forgot password?</button></div>
    <button className="eds-button eds-button--primary eds-button--lg eds-button--block auth-submit" type="submit" disabled={busy}>{busy?"Signing in…":"Sign in"}</button>
   </form>
   <div className="auth-divider"><span>OR USE YOUR INSTITUTION</span></div>
   <button className="eds-button eds-button--secondary eds-button--lg eds-button--block" type="button" onClick={()=>setNotice("Institution single sign-on is not connected in this environment. Use your school account to sign in.")}>Continue with school SSO</button>
   <p className="auth-security"><span aria-hidden="true">◆</span>Your access follows your institution’s roles and permissions. Never share your password.</p>
  </section><footer className="auth-footer">EDU 360 <span>·</span> A clearer view of learning</footer>
 </main>;
}