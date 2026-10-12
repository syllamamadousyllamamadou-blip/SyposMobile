import { useState, useEffect } from 'react';
import { app, db } from './firebase';
import { getAuth, signInWithEmailAndPassword, onAuthStateChanged, signOut, type User } from 'firebase/auth';
import { collection, doc, onSnapshot, updateDoc, getDocs } from 'firebase/firestore';
import { Shield, Users, Store, LogOut, Lock, BookOpen, Search } from 'lucide-react';
import './App.css';
import ClientDashboard from './ClientDashboard';

interface AppUser {
  shopName?: string;
  phoneNumber?: string;
  id: string; // The uid
  email: string;
  plan: string;
  expiry: number;
  allowedDevices: number;
  devices: string[];
}

function App() {
  const [user, setUser] = useState<User | null>(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loginError, setLoginError] = useState('');
  const [usersList, setUsersList] = useState<AppUser[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [filterTab, setFilterTab] = useState('ALL');
  const [viewingDashboard, setViewingDashboard] = useState<string | null>(null);

  const auth = getAuth(app);

  useEffect(() => {
    const unsub = onAuthStateChanged(auth, (u) => {
      setUser(u);
      setAuthLoading(false);
    });
    return () => unsub();
  }, [auth]);

  useEffect(() => {
    if (user && isAdmin(user)) {
      const unsub = onSnapshot(collection(db, 'users'), (snapshot) => {
        const list: AppUser[] = [];
        snapshot.forEach(doc => {
          list.push({ id: doc.id, ...doc.data() } as AppUser);
        });
        setUsersList(list);
      });
      return () => unsub();
    }
  }, [user]);

  const isAdmin = (u: User | null) => {
    if (!u) return false;
    return u.email === 'admin@kayo.com' || u.email === 'contact@kayo.com' || u.email === 'syllasyllas@icloud.com';
  };

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoginError('');
    try {
      await signInWithEmailAndPassword(auth, email, password);
    } catch (err: any) {
      setLoginError("Email ou mot de passe incorrect.");
    }
  };

  const handleLogout = async () => {
    await signOut(auth);
  };

  const handleBlock = async (userId: string) => {
    if (window.confirm("Bloquer l'accès pour ce compte ?")) {
      await updateDoc(doc(db, 'users', userId), {
        plan: 'BLOCKED'
      });
    }
  };

  const handleRenew = async (u: AppUser, planType: string) => {
    if (window.confirm(`Activer l'abonnement ${planType} pour ce client ?`)) {
      let expDate = 0;
      if (planType === 'MONTHLY') expDate = Date.now() + 30 * 24 * 60 * 60 * 1000;
      else if (planType === 'YEARLY') expDate = Date.now() + 365 * 24 * 60 * 60 * 1000;
      
      await updateDoc(doc(db, 'users', u.id), {
        plan: planType,
        expiry: expDate
      });
    }
  };

  const handleChangeDevices = async (u: AppUser) => {
    const res = window.prompt("Nombre de caisses (appareils) autorisées pour ce client :", u.allowedDevices.toString());
    if (res && !isNaN(Number(res))) {
      await updateDoc(doc(db, 'users', u.id), {
        allowedDevices: Number(res)
      });
    }
  };

  const exportUserJson = async (u: AppUser) => {
    try {
      const productsSnap = await getDocs(collection(db, 'shops', u.id, 'products'));
      const ticketsSnap = await getDocs(collection(db, 'shops', u.id, 'tickets'));
      
      const products = productsSnap.docs.map(d => d.data());
      const tickets = ticketsSnap.docs.map(d => d.data());
      
      const data = {
        user: u,
        products,
        tickets,
        exportDate: new Date().toISOString()
      };
      
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `kayo_backup_${u.shopName || u.id}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error(err);
      alert("Erreur lors de l'exportation JSON");
    }
  };

  if (authLoading) return <div className="loading">Chargement...</div>;

  if (!user) {
    return (
      <div className="login-container">
        <div className="login-card">
          <Shield size={48} className="logo-icon" style={{ margin: '0 auto 16px', display: 'block', color: '#3b82f6' }} />
          <h2 style={{ textAlign: 'center', marginBottom: '24px' }}>Connexion à votre espace KAYO</h2>
          <form onSubmit={handleLogin} className="activation-form">
            <div className="form-group">
              <label>Adresse Email</label>
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required placeholder="votre@email.com" />
            </div>
            <div className="form-group">
              <label>Mot de passe</label>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
            </div>
            {loginError && <p style={{ color: 'red', marginTop: '10px' }}>{loginError}</p>}
            <button type="submit" className="btn-primary" style={{ marginTop: '16px', width: '100%' }}>Se connecter</button>
          </form>
        </div>
      </div>
    );
  }

  // If user is logged in, show Admin Dashboard or Client Dashboard
  if (viewingDashboard) {
    return (
      <div style={{ position: 'relative' }}>
        <button onClick={() => setViewingDashboard(null)} className="btn-secondary" style={{ position: 'absolute', top: '20px', right: '20px', zIndex: 100 }}>
          Fermer Dashboard
        </button>
        <ClientDashboard onBack={() => setViewingDashboard(null)} user={{ uid: viewingDashboard }} />
      </div>
    );
  }

  if (!isAdmin(user)) {
    return (
      <div style={{ position: 'relative' }}>
        <button onClick={handleLogout} className="btn-secondary" style={{ position: 'absolute', top: '20px', right: '20px', zIndex: 100 }}>
          Déconnexion
        </button>
        <ClientDashboard onBack={handleLogout} user={user} />
      </div>
    );
  }

  return (
    <div className="admin-dashboard">
      <aside className="sidebar">
        <div className="logo-area">
          <Shield className="logo-icon" size={32} />
          <h1>KAYO Admin</h1>
        </div>
        <nav className="nav-menu">
          <a href="#" className="nav-item active"><Users size={20} /> Abonnés (Clients)</a>
          <a href="/manuel/" target="_blank" className="nav-item" style={{ backgroundColor: '#f0fdf4', color: '#166534', fontWeight: 'bold' }}><BookOpen size={20} /> Manuel d'Utilisation</a>
          <a href="#" className="nav-item" onClick={handleLogout}><LogOut size={20} /> Déconnexion</a>
        </nav>
      </aside>

      <main className="main-content">
        <header className="header">
          <h2>Gestion des Abonnements KAYO SaaS</h2>
          <p>Gérez les souscriptions et le nombre d'appareils de vos clients.</p>
        </header>

        <div className="content-grid" style={{ gridTemplateColumns: '1fr' }}>
          <section className="card devices-card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h3>Comptes Inscrits ({usersList.length})</h3>
                  <div className="input-wrapper" style={{ width: '250px' }}>
                    <Search size={18} className="input-icon" />
                    <input 
                      type="text" 
                      placeholder="Rechercher email..." 
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      style={{ padding: '8px 8px 8px 36px' }}
                    />
                  </div>
                </div>
                
            <div style={{ display: 'flex', gap: '8px', marginBottom: '16px', overflowX: 'auto', paddingBottom: '4px' }}>
              <button onClick={() => setFilterTab('ALL')} className={filterTab === 'ALL' ? 'btn-primary' : 'btn-secondary'} style={{ padding: '8px 16px', borderRadius: '20px' }}>Tous</button>
              <button onClick={() => setFilterTab('ACTIVE')} className={filterTab === 'ACTIVE' ? 'btn-primary' : 'btn-secondary'} style={{ padding: '8px 16px', borderRadius: '20px' }}>Actifs</button>
              <button onClick={() => setFilterTab('TRIAL')} className={filterTab === 'TRIAL' ? 'btn-primary' : 'btn-secondary'} style={{ padding: '8px 16px', borderRadius: '20px' }}>Essai</button>
              <button onClick={() => setFilterTab('BLOCKED')} className={filterTab === 'BLOCKED' ? 'btn-primary' : 'btn-secondary'} style={{ padding: '8px 16px', borderRadius: '20px' }}>Bloqués</button>
            </div>
            
            <div className="devices-list">
              {usersList.filter(u => {
                const matchesSearch = ((u.email || '').toLowerCase().includes(searchQuery.toLowerCase()) || (u.shopName || '').toLowerCase().includes(searchQuery.toLowerCase()) || (u.phoneNumber || '').includes(searchQuery));
                if (!matchesSearch) return false;
                if (filterTab === 'ACTIVE') return u.plan === 'MONTHLY' || u.plan === 'YEARLY' || u.plan === 'LIFETIME';
                if (filterTab === 'TRIAL') return u.plan === 'TRIAL' || !u.plan;
                if (filterTab === 'BLOCKED') return u.plan === 'BLOCKED';
                return true;
              }).map(u => (
                <div key={u.id} className="device-item">
                  <div className="device-info">
                    <strong>{u.shopName || 'Boutique sans nom'}</strong> <span style={{fontSize: '0.8rem', color: 'gray'}}>{u.email}</span> <span style={{fontSize: '0.8rem', color: 'gray'}}>• {u.phoneNumber || 'Pas de numéro'}</span>
                    <div style={{ marginTop: '4px', fontSize: '0.85rem', color: '#3b82f6', fontWeight: 'bold' }}>
                      ID Client (à copier) : {u.id}
                    </div>
                    <div className="client-info"><Store size={14}/> {u.devices?.length || 0} / {u.allowedDevices} Appareils utilisés</div>
                    <div className="badges" style={{ marginTop: '8px' }}>
                      <span className={'badge ' + (u.plan === 'BLOCKED' ? 'blocked' : 'plan')}>
                        {u.plan}
                      </span>
                      {u.expiry > 0 && <span className="badge status" style={{ backgroundColor: '#f3f4f6', color: '#374151' }}>
                        Exp: {new Date(u.expiry).toLocaleDateString()}
                      </span>}
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: '8px', flexDirection: 'column' }}>
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <button onClick={() => setViewingDashboard(u.id)} className="btn-secondary" style={{ flex: 1, padding: '6px', fontSize: '0.8rem' }}>
                        Dashboard ☁️
                      </button>
                    </div>
                    <button onClick={() => exportUserJson(u)} className="btn-secondary" style={{ padding: '6px', fontSize: '0.8rem', backgroundColor: '#e2e8f0', color: '#0f172a' }}>
                      📥 Exporter JSON (Sauvegarde)
                    </button>
                    <button onClick={() => handleRenew(u, 'MONTHLY')} className="btn-primary" style={{ padding: '6px', fontSize: '0.8rem', backgroundColor: '#10B981' }}>
                      Abo 30 Jours
                    </button>
                    <button onClick={() => handleRenew(u, 'YEARLY')} className="btn-primary" style={{ padding: '6px', fontSize: '0.8rem', backgroundColor: '#3b82f6' }}>
                      Abo 1 An
                    </button>
                    <button onClick={() => handleRenew(u, 'LIFETIME')} className="btn-primary" style={{ padding: '6px', fontSize: '0.8rem', backgroundColor: '#8b5cf6' }}>
                      Abo À Vie
                    </button>
                    <button onClick={() => handleChangeDevices(u)} className="btn-primary" style={{ padding: '6px', fontSize: '0.8rem', backgroundColor: '#f59e0b', color: 'white' }}>
                      Changer limite caisses
                    </button>
                    {u.plan !== 'BLOCKED' && (
                      <button onClick={() => handleBlock(u.id)} className="btn-delete btn-block">
                        <Lock size={14} /> Bloquer
                      </button>
                    )}
                  </div>
                </div>
              ))}
              {usersList.length === 0 && (
                <div className="empty-state">
                  <p>Aucun compte client pour le moment.</p>
                </div>
              )}
            </div>
          </section>
        </div>
      </main>
    </div>
  );
}

export default App;
