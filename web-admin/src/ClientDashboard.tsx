import React, { useState, useEffect } from 'react';
import { db } from './firebase';
import { collection, getDocs } from 'firebase/firestore';
import { Store, Activity, ArrowLeft, Filter, RefreshCw, ShoppingCart, DollarSign, Package } from 'lucide-react';
import './App.css';

export default function ClientDashboard({ onBack, user }: { onBack: () => void, user?: any }) {
  const [shopId, setShopIdState] = useState(user ? user.uid : ((localStorage.getItem('clientShopId') as string) || ''));
  
  const setShopId = (id: string) => {
    localStorage.setItem('clientShopId', id);
    setShopIdState(id);
  };
  
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<any>(null);
  
  // Date Filters
  const [startDate, setStartDate] = useState<string>('');
  const [endDate, setEndDate] = useState<string>('');

  useEffect(() => {
    if (!shopId.trim()) return;
    
    setLoading(true);
    setError(null);
    const safeShopId = shopId.trim();

    let unsubProducts: any;
    let unsubTickets: any;
    let unsubExpenses: any;

    import('firebase/firestore').then(({ onSnapshot, collection }) => {
      unsubProducts = onSnapshot(collection(db, 'shops', safeShopId, 'products'), (snap) => {
        const products = snap.docs.map(doc => doc.data());
        setData((prev: any) => ({ ...prev, products, timestamp: Date.now() }));
        setLoading(false);
      }, (err) => {
        console.error(err);
        setError("Erreur de connexion en temps réel (Produits).");
        setLoading(false);
      });

      unsubTickets = onSnapshot(collection(db, 'shops', safeShopId, 'tickets'), (snap) => {
        const tickets = snap.docs.map(doc => doc.data());
        setData((prev: any) => ({ ...prev, tickets, timestamp: Date.now() }));
      }, (err) => {
        console.error(err);
        setError("Erreur de connexion en temps réel (Ventes).");
      });

      unsubExpenses = onSnapshot(collection(db, 'shops', safeShopId, 'expenses'), (snap) => {
        const expenses = snap.docs.map(doc => doc.data());
        setData((prev: any) => ({ ...prev, expenses, timestamp: Date.now() }));
      }, (err) => {
        console.error(err);
      });
    });

    return () => {
      if (unsubProducts) unsubProducts();
      if (unsubTickets) unsubTickets();
      if (unsubExpenses) unsubExpenses();
    };
  }, [shopId]);

  const fetchDashboard = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    // In real-time mode, manual fetch just resets loading if needed
    setLoading(true);
    setTimeout(() => setLoading(false), 500);
  };

  if (data) {
    // Analytics
    let rawTickets = data.tickets || [];
    let rawExpenses = data.expenses || [];
    
    // Filtering by date
    if (startDate) {
      const startMs = new Date(startDate).getTime();
      rawTickets = rawTickets.filter((t: any) => t.date >= startMs);
      rawExpenses = rawExpenses.filter((e: any) => e.date >= startMs);
    }
    if (endDate) {
      const endMs = new Date(endDate).getTime() + 86399999; // end of day
      rawTickets = rawTickets.filter((t: any) => t.date <= endMs);
      rawExpenses = rawExpenses.filter((e: any) => e.date <= endMs);
    }

    // Merge and sort recent activities
    const allActivities = [
      ...rawTickets.map((t: any) => ({ ...t, type: 'SALE', time: t.date || 0 })),
      ...rawExpenses.map((e: any) => ({ ...e, type: 'EXPENSE', time: e.date || 0 }))
    ].sort((a, b) => b.time - a.time);
    
    let totalSales = 0;
    let totalExpenses = 0;
    let totalPurchaseCost = 0;
    let productsSold = 0;

    const productsMap = new Map();
    if (data.products) {
      data.products.forEach((p: any) => {
        productsMap.set(p.id, p);
      });
    }

    rawTickets.forEach((t: any) => {
      if (t.status !== 'CANCELLED') {
        totalSales += t.totalAmount || t.subTotal || 0;
        productsSold += t.totalItemsCount || 1;
        totalPurchaseCost += t.totalPurchaseCost || 0;
      }
    });

    let totalStockValue = 0;
    let totalStockCost = 0;
    if (data.products) {
      data.products.forEach((p: any) => {
        const qty = p.stockQuantity || 0;
        if (qty > 0) {
          totalStockValue += qty * (p.price || 0);
          totalStockCost += qty * (p.costPrice || 0);
        }
      });
    }

    rawExpenses.forEach((e: any) => {
      totalExpenses += e.amount || 0;
    });

    const netProfit = totalSales - totalPurchaseCost - totalExpenses;

    const exportZReportCSV = () => {
      let csvContent = "data:text/csv;charset=utf-8,\uFEFF";
      csvContent += "Type,Date,Reference,Montant,Details\n";
      
      rawTickets.forEach((t: any) => {
        const isCancelled = t.status === 'CANCELLED';
        const dateStr = new Date(t.date).toLocaleString();
        const amount = isCancelled ? 0 : (t.totalAmount || t.subTotal || 0);
        const details = t.itemsSummary || (t.items ? t.items.map((i:any)=>`${i.productName}(x${i.quantity})`).join(" | ") : "");
        const type = isCancelled ? "VENTE ANNULÉE" : "VENTE";
        csvContent += `${type},"${dateStr}","${t.id}",${amount},"${details}"\n`;
      });
      
      rawExpenses.forEach((e: any) => {
        const dateStr = new Date(e.date).toLocaleString();
        const amount = e.amount || 0;
        const details = e.reason || "";
        csvContent += `DEPENSE,"${dateStr}","${e.id}",${amount},"${details}"\n`;
      });
      
      const encodedUri = encodeURI(csvContent);
      const link = document.createElement("a");
      link.setAttribute("href", encodedUri);
      link.setAttribute("download", `rapport_Z_${shopId}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    };

    const exportStockCSV = () => {
      let csvContent = "data:text/csv;charset=utf-8,\uFEFF";
      csvContent += "Produit,Code,Prix Vente,Prix Achat,Marge (%),Stock Restant,Valeur Vente (CFA),Valeur Achat (CFA),Benefice Estime,Statut\n";
      
      if (data.products) {
        data.products.forEach((p: any) => {
          const prixVente = p.salePrice || 0;
          const prixAchat = p.costPrice || 0;
          const stock = p.stockQuantity || 0;
          const alerte = p.alertStock || 5;
          const marge = prixVente > 0 ? ((prixVente - prixAchat) / prixVente) * 100 : 0;
          const valeurVente = stock > 0 ? stock * prixVente : 0;
          const valeurAchat = stock > 0 ? stock * prixAchat : 0;
          const beneficeEstime = valeurVente - valeurAchat;
          
          let statut = "En Stock";
          if (stock <= 0) statut = "EPUISE";
          else if (stock <= alerte) statut = "STOCK FAIBLE";

          csvContent += `"${p.name || ''}","${p.barcode || ''}",${prixVente},${prixAchat},${marge.toFixed(1)},${stock},${valeurVente},${valeurAchat},${beneficeEstime},"${statut}"\n`;
        });
      }
      
      const encodedUri = encodeURI(csvContent);
      const link = document.createElement("a");
      link.setAttribute("href", encodedUri);
      link.setAttribute("download", `inventaire_stock_${shopId}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    };

    return (
      <div className="admin-layout" style={{ backgroundColor: '#0f172a', minHeight: '100vh', padding: '20px', color: '#f1f5f9' }}>
        <div style={{ maxWidth: '1100px', margin: '0 auto', width: '100%' }}>
          <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px', flexWrap: 'wrap', gap: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              <button onClick={() => setData(null)} className="btn-secondary" style={{ padding: '10px', display: 'flex', alignItems: 'center', backgroundColor: '#1e293b', border: '1px solid #334155', color: '#fff' }}>
                <ArrowLeft size={18} />
              </button>
              <div>
                <h2 style={{ margin: 0, fontSize: '24px', fontWeight: 'bold', color: '#fff' }}>Dashboard : {shopId}</h2>
                <p style={{ margin: '4px 0 0', color: '#94a3b8', fontSize: '14px' }}>Temps Réel Cloud Sync</p>
              </div>
            </div>
            
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', backgroundColor: '#1e293b', padding: '6px 16px', borderRadius: '12px', border: '1px solid #334155' }}>
                <Filter size={16} color="#94a3b8" />
                <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} style={{ border: 'none', outline: 'none', background: 'transparent', color: '#fff', colorScheme: 'dark' }} />
                <span style={{ color: '#64748b' }}>au</span>
                <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} style={{ border: 'none', outline: 'none', background: 'transparent', color: '#fff', colorScheme: 'dark' }} />
                {(startDate || endDate) && (
                  <button onClick={() => { setStartDate(''); setEndDate(''); }} style={{ background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer', fontSize: '12px', marginLeft: '8px' }}>
                    Effacer
                  </button>
                )}
              </div>
              <button 
                onClick={exportZReportCSV} 
                className="btn-secondary" 
                style={{ display: 'flex', alignItems: 'center', gap: '8px', backgroundColor: '#475569', border: 'none', padding: '10px 16px', borderRadius: '12px', color: 'white', cursor: 'pointer' }}
              >
                Rapport Z (CSV)
              </button>
              <button 
                onClick={exportStockCSV} 
                className="btn-secondary" 
                style={{ display: 'flex', alignItems: 'center', gap: '8px', backgroundColor: '#059669', border: 'none', padding: '10px 16px', borderRadius: '12px', color: 'white', cursor: 'pointer' }}
              >
                Stock (CSV)
              </button>
              <button 
                onClick={async () => {
                  await fetchDashboard();
                }} 
                className="btn-primary" 
                style={{ display: 'flex', alignItems: 'center', gap: '8px', backgroundColor: '#3b82f6', border: 'none', padding: '10px 20px', borderRadius: '12px', color: 'white', fontWeight: 'bold', cursor: 'pointer' }}
              >
                <RefreshCw size={18} className={loading ? "spin" : ""} />
                {loading ? "Synchro..." : "Actualiser"}
              </button>
            </div>
          </header>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '20px', marginBottom: '32px' }}>
            <div className="card" style={{ padding: '24px', backgroundColor: '#1e293b', borderRadius: '16px', border: '1px solid #334155', position: 'relative', overflow: 'hidden' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <p style={{ margin: 0, color: '#94a3b8', fontSize: '14px', fontWeight: '600', textTransform: 'uppercase' }}>Chiffre d'Affaires</p>
                  <h2 style={{ margin: '12px 0 0', color: '#fff', fontSize: '32px' }}>{totalSales.toLocaleString()} <span style={{fontSize:'16px', color:'#10b981'}}>FCFA</span></h2>
                </div>
                <div style={{ backgroundColor: 'rgba(16, 185, 129, 0.1)', padding: '12px', borderRadius: '12px' }}>
                  <DollarSign size={24} color="#10b981" />
                </div>
              </div>
            </div>
            
            <div className="card" style={{ padding: '24px', backgroundColor: '#1e293b', borderRadius: '16px', border: '1px solid #334155', position: 'relative', overflow: 'hidden' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <p style={{ margin: 0, color: '#94a3b8', fontSize: '14px', fontWeight: '600', textTransform: 'uppercase' }}>Dépenses</p>
                  <h2 style={{ margin: '12px 0 0', color: '#fff', fontSize: '32px' }}>{totalExpenses.toLocaleString()} <span style={{fontSize:'16px', color:'#ef4444'}}>FCFA</span></h2>
                </div>
                <div style={{ backgroundColor: 'rgba(239, 68, 68, 0.1)', padding: '12px', borderRadius: '12px' }}>
                  <Activity size={24} color="#ef4444" />
                </div>
              </div>
            </div>

            <div className="card" style={{ padding: '24px', backgroundColor: '#1e293b', borderRadius: '16px', border: '1px solid #334155', position: 'relative', overflow: 'hidden' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <p style={{ margin: 0, color: '#94a3b8', fontSize: '14px', fontWeight: '600', textTransform: 'uppercase' }}>Bénéfice Net</p>
                  <h2 style={{ margin: '12px 0 0', color: '#fff', fontSize: '32px' }}>{netProfit.toLocaleString()} <span style={{fontSize:'16px', color: netProfit >= 0 ? '#10b981' : '#ef4444'}}>FCFA</span></h2>
                </div>
                <div style={{ backgroundColor: 'rgba(59, 130, 246, 0.1)', padding: '12px', borderRadius: '12px' }}>
                  <Activity size={24} color="#3b82f6" />
                </div>
              </div>
            </div>

            <div className="card" style={{ padding: '24px', backgroundColor: '#1e293b', borderRadius: '16px', border: '1px solid #334155', position: 'relative', overflow: 'hidden' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <p style={{ margin: 0, color: '#94a3b8', fontSize: '14px', fontWeight: '600', textTransform: 'uppercase' }}>Valeur du Stock (Revente)</p>
                  <h2 style={{ margin: '12px 0 0', color: '#fff', fontSize: '32px' }}>{totalStockValue.toLocaleString()} <span style={{fontSize:'16px', color:'#f59e0b'}}>FCFA</span></h2>
                  <p style={{ margin: '8px 0 0', color: '#64748b', fontSize: '12px' }}>Coût d'achat: {totalStockCost.toLocaleString()} FCFA</p>
                </div>
                <div style={{ backgroundColor: 'rgba(245, 158, 11, 0.1)', padding: '12px', borderRadius: '12px' }}>
                  <Package size={24} color="#f59e0b" />
                </div>
              </div>
            </div>

            <div className="card" style={{ padding: '24px', backgroundColor: '#1e293b', borderRadius: '16px', border: '1px solid #334155', position: 'relative', overflow: 'hidden' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <p style={{ margin: 0, color: '#94a3b8', fontSize: '14px', fontWeight: '600', textTransform: 'uppercase' }}>Articles Vendus</p>
                  <h2 style={{ margin: '12px 0 0', color: '#fff', fontSize: '32px' }}>{productsSold} <span style={{fontSize:'16px', color:'#8b5cf6'}}>Unités</span></h2>
                </div>
                <div style={{ backgroundColor: 'rgba(139, 92, 246, 0.1)', padding: '12px', borderRadius: '12px' }}>
                  <Package size={24} color="#8b5cf6" />
                </div>
              </div>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '20px' }}>
            <div className="card" style={{ padding: '24px', backgroundColor: '#1e293b', borderRadius: '16px', border: '1px solid #334155' }}>
              <h3 style={{ marginTop: 0, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px', color: '#fff', fontSize: '18px' }}>
                <ShoppingCart size={20} color="#3b82f6" />
                Aperçu du Catalogue
              </h3>
              <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <li style={{ padding: '16px', backgroundColor: '#0f172a', borderRadius: '12px', border: '1px solid #334155', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{color: '#94a3b8'}}>Total des produits</span>
                  <strong style={{color: '#fff', fontSize: '18px'}}>{data.products?.length || 0}</strong>
                </li>
                <li style={{ padding: '16px', backgroundColor: '#0f172a', borderRadius: '12px', border: '1px solid #334155', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{color: '#94a3b8'}}>Volume total en stock</span>
                  <strong style={{color: '#fff', fontSize: '18px'}}>{data.products?.reduce((sum: number, p: any) => sum + (p.stockQuantity || 0), 0) || 0}</strong>
                </li>
                <li style={{ padding: '16px', backgroundColor: '#0f172a', borderRadius: '12px', border: '1px solid #334155', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{color: '#94a3b8'}}>Ventes réalisées</span>
                  <strong style={{color: '#fff', fontSize: '18px'}}>{data.tickets?.length || 0}</strong>
                </li>
              </ul>
            </div>

            {allActivities.length > 0 && (
              <div className="card" style={{ padding: '24px', backgroundColor: '#1e293b', borderRadius: '16px', border: '1px solid #334155', gridColumn: 'span 2' }}>
                <h3 style={{ marginTop: 0, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px', color: '#fff', fontSize: '18px' }}>
                  <Activity size={20} color="#8b5cf6" />
                  Historique Récent
                </h3>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', maxHeight: '400px', overflowY: 'auto', paddingRight: '8px' }}>
                  {allActivities.map((act: any, i: number) => {
                    const isSale = act.type === 'SALE';
                    const isCancelled = isSale && act.status === 'CANCELLED';
                    
                    let paymentDisplay = act.paymentMethod || '';
                    if (act.paymentMethod === 'SPLIT') {
                      paymentDisplay = `Fractionné (Espèces: ${act.splitCashAmount || 0}, ${act.splitOtherMethod || 'Autre'}: ${act.splitOtherAmount || 0})`;
                    }

                    return (
                      <div key={i} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px', backgroundColor: '#0f172a', borderRadius: '12px', borderLeft: `4px solid ${isCancelled ? '#64748B' : (isSale ? '#10B981' : '#EF4444')}`, opacity: isCancelled ? 0.6 : 1 }}>
                        <div>
                          <strong style={{ color: '#fff', display: 'block', fontSize: '16px', textDecoration: isCancelled ? 'line-through' : 'none' }}>
                            {isSale ? `Vente #${act.ticketNumber || act.id}` : `Dépense: ${act.category || act.description}`}
                          </strong>
                          <div style={{ fontSize: '13px', color: '#94a3b8', display: 'flex', gap: '16px', flexWrap: 'wrap', marginTop: '6px' }}>
                            <span>⏱ {act.time ? new Date(act.time).toLocaleString() : 'Date inconnue'}</span>
                            {isCancelled && <span style={{color: '#EF4444', fontWeight: 'bold'}}>ANNULÉ</span>}
                            {isSale && act.sellerName && <span style={{color: '#cbd5e1'}}>👤 Vendeur: {act.sellerName}</span>}
                            {isSale && paymentDisplay && <span>💳 {paymentDisplay}</span>}
                          </div>
                        </div>
                        <div style={{ fontWeight: 'bold', fontSize: '18px', color: isCancelled ? '#64748B' : (isSale ? '#10B981' : '#EF4444'), textDecoration: isCancelled ? 'line-through' : 'none' }}>
                          {isSale ? '+' : '-'}{(act.totalAmount || act.subTotal || act.amount || 0).toLocaleString()}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="login-container" style={{ backgroundColor: '#0f172a', minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px' }}>
      <button onClick={onBack} className="btn-secondary" style={{ position: 'absolute', top: '20px', left: '20px', display: 'flex', alignItems: 'center', gap: '8px', backgroundColor: 'transparent', color: '#fff', border: 'none', cursor: 'pointer' }}>
        <ArrowLeft size={16} /> Retour à l'accueil
      </button>
      <div className="login-card" style={{ maxWidth: '450px', width: '100%', backgroundColor: '#1e293b', padding: '40px', borderRadius: '24px', boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.5)', border: '1px solid #334155' }}>
        <Store size={56} className="logo-icon" style={{ margin: '0 auto 24px', display: 'block', color: '#3b82f6' }} />
        <h2 style={{ textAlign: 'center', marginBottom: '12px', color: '#f8fafc', fontSize: '28px' }}>Gestion Cloud</h2>
        <p style={{ textAlign: 'center', color: '#94a3b8', marginBottom: '32px', fontSize: '15px', lineHeight: '1.5' }}>
          Connectez-vous à votre commerce KAYO pour suivre vos performances en temps réel.
        </p>
        
        <form onSubmit={fetchDashboard} className="activation-form">
          <div className="form-group" style={{ marginBottom: '24px' }}>
            <label style={{ color: '#cbd5e1', marginBottom: '8px', display: 'block', fontSize: '14px', fontWeight: '500' }}>Nom du Magasin (Identifiant Cloud)</label>
            <input 
              type="text" 
              value={shopId} 
              onChange={(e) => setShopId(e.target.value)} 
              required 
              placeholder="Ex: KAYO COMMERCE" 
              style={{ fontSize: '16px', padding: '16px', width: '100%', borderRadius: '12px', border: '1px solid #334155', backgroundColor: '#0f172a', color: '#fff', boxSizing: 'border-box' }}
            />
          </div>
          
          {error && <div style={{ padding: '16px', backgroundColor: 'rgba(239, 68, 68, 0.1)', color: '#ef4444', borderRadius: '12px', fontSize: '14px', marginBottom: '24px', border: '1px solid rgba(239, 68, 68, 0.2)' }}>{error}</div>}
          
          <button type="submit" className="btn-primary" style={{ width: '100%', padding: '16px', borderRadius: '12px', backgroundColor: '#3b82f6', color: '#fff', fontSize: '16px', fontWeight: '600', border: 'none', cursor: 'pointer', transition: 'background-color 0.2s' }} disabled={loading}>
            {loading ? 'Connexion au Cloud...' : 'Accéder au Tableau de bord'}
          </button>
        </form>
      </div>
    </div>
  );
}


