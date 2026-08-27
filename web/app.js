/**
 * SYPOS Mobile Pro — iOS Web PWA Engine v2.2
 * 100% Offline IndexedDB + Web Bluetooth Thermal ESC/POS Driver + Camera Barcode Scanner
 */

// ==========================================
// 1. DEFAULT DATA & STATE
// ==========================================
const STATE = {
  products: [],
  categories: [],
  tickets: [],
  customers: [],
  expenses: [],
  promoCodes: [],
  heldSales: [],
  settings: {
    shopName: "SYPOS COMMERCE",
    shopAddress: "Abidjan, Côte d'Ivoire",
    shopPhone: "+225 07 58 24 55 30",
    receiptFooter: "Merci de votre visite et à bientôt !",
    sellerName: "Vendeur 1",
    showPublisherSignature: true,
    publisherSignatureText: "Solution: SYPOS MOBILE 0758245530",
    taxEnabled: false,
    taxRatePercent: 18.0,
    adminPin: "1234",
    allowNegativeStock: false,
    printerWidth: "58",
    isLicensed: false,
    licenseKey: "",
    licenseType: "Non activé",
    licenseExpiryDate: 0
  },
  cart: [],
  appliedPromo: null,
  activeCategory: "",
  html5QrCode: null,
  pendingPinAction: null,
  bleDevice: null,
  bleCharacteristic: null
};

// ==========================================
// 2. LOCAL PERSISTENCE (LocalStorage / IndexedDB)
// ==========================================
function loadFromStorage() {
  const s = localStorage.getItem('sypos_data_v2');
  if (s) {
    try {
      const parsed = JSON.parse(s);
      Object.assign(STATE.settings, parsed.settings || {});
      STATE.products = parsed.products || [];
      STATE.categories = parsed.categories || [];
      STATE.tickets = parsed.tickets || [];
      STATE.customers = parsed.customers || [];
      STATE.expenses = parsed.expenses || [];
      STATE.promoCodes = parsed.promoCodes || [];
      STATE.heldSales = parsed.heldSales || [];
    } catch (e) {
      console.error(e);
    }
  } else {
    // Seed initial dummy data
    STATE.categories = [
      { id: "1", name: "Alimentation", color: "#10B981" },
      { id: "2", name: "Boissons", color: "#3B82F6" },
      { id: "3", name: "Hygiène", color: "#EC4899" },
      { id: "4", name: "Divers", color: "#8B5CF6" }
    ];
    STATE.products = [
      { id: "p1", name: "Riz Parfumé 5kg", salePrice: 4500, costPrice: 3800, stock: 25, barcode: "61811001", catId: "1" },
      { id: "p2", name: "Huile Dinor 1L", salePrice: 1300, costPrice: 1100, stock: 40, barcode: "61811002", catId: "1" },
      { id: "p3", name: "Coca Cola 33cl", salePrice: 500, costPrice: 350, stock: 60, barcode: "61811003", catId: "2" },
      { id: "p4", name: "Eau Awa 1.5L", salePrice: 400, costPrice: 280, stock: 80, barcode: "61811004", catId: "2" },
      { id: "p5", name: "Savon Lux 150g", salePrice: 500, costPrice: 350, stock: 50, barcode: "61811005", catId: "3" },
      { id: "p6", name: "Lait Bonnet Rouge", salePrice: 700, costPrice: 550, stock: 35, barcode: "61811006", catId: "1" }
    ];
    STATE.customers = [
      { id: "c1", name: "Client Comptant", phone: "", debt: 0 },
      { id: "c2", name: "M. Kouamé", phone: "0708091011", debt: 2500 },
      { id: "c3", name: "Mme Traoré", phone: "0506070809", debt: 0 }
    ];
    STATE.promoCodes = [
      { id: "pr1", code: "SOLDES10", discountPercent: 10, isActive: true }
    ];
    saveToStorage();
  }
}

function saveToStorage() {
  localStorage.setItem('sypos_data_v2', JSON.stringify({
    settings: STATE.settings,
    products: STATE.products,
    categories: STATE.categories,
    tickets: STATE.tickets,
    customers: STATE.customers,
    expenses: STATE.expenses,
    promoCodes: STATE.promoCodes,
    heldSales: STATE.heldSales
  }));
}

// ==========================================
// 3. TAB NAVIGATION
// ==========================================
function initTabs() {
  document.querySelectorAll('.app-tabbar .tab-item').forEach(tab => {
    tab.addEventListener('click', (e) => {
      e.preventDefault();
      const tabName = tab.dataset.tab;
      document.querySelectorAll('.app-tabbar .tab-item').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');

      document.querySelectorAll('.view-section').forEach(sec => sec.classList.remove('active'));
      const activeSec = document.getElementById(`view-${tabName}`);
      if (activeSec) activeSec.classList.add('active');

      // Refresh view contents
      if (tabName === 'pos') renderPos();
      else if (tabName === 'products') renderProducts();
      else if (tabName === 'history') renderHistory();
      else if (tabName === 'reports') renderReports();
      else if (tabName === 'customers') renderCustomers();
      else if (tabName === 'tools') renderTools();
      else if (tabName === 'settings') renderSettings();
    });
  });
}

// ==========================================
// 4. POS & CART LOGIC
// ==========================================
function renderPos() {
  const catScroll = document.getElementById('posCategoryScroll');
  catScroll.innerHTML = `<button class="cat-pill ${STATE.activeCategory === '' ? 'active' : ''}" data-cat="">Tous</button>`;
  STATE.categories.forEach(c => {
    catScroll.innerHTML += `<button class="cat-pill ${STATE.activeCategory === c.id ? 'active' : ''}" data-cat="${c.id}">${c.name}</button>`;
  });

  catScroll.querySelectorAll('.cat-pill').forEach(pill => {
    pill.addEventListener('click', () => {
      STATE.activeCategory = pill.dataset.cat;
      renderPos();
    });
  });

  const searchVal = document.getElementById('posSearchInput').value.toLowerCase().trim();
  const grid = document.getElementById('posProductGrid');
  grid.innerHTML = '';

  const filtered = STATE.products.filter(p => {
    const matchCat = !STATE.activeCategory || p.catId === STATE.activeCategory;
    const matchSearch = !searchVal || p.name.toLowerCase().includes(searchVal) || (p.barcode && p.barcode.includes(searchVal));
    return matchCat && matchSearch;
  });

  filtered.forEach(p => {
    const card = document.createElement('div');
    card.className = 'product-card';
    const isLow = p.stock <= 5;
    card.innerHTML = `
      <span class="stock-badge ${isLow ? 'stock-low' : 'stock-ok'}">${p.stock}</span>
      <div class="product-name">${p.name}</div>
      <div class="product-price">${p.salePrice.toLocaleString('fr-FR')} CFA</div>
    `;
    card.addEventListener('click', () => addToCart(p));
    grid.appendChild(card);
  });

  updateFloatingCart();
}

function addToCart(product) {
  const existing = STATE.cart.find(i => i.product.id === product.id);
  if (existing) {
    if (!STATE.settings.allowNegativeStock && existing.qty >= product.stock) {
      alert(`⚠️ Stock insuffisant pour ${product.name} (Stock actuel: ${product.stock})`);
      return;
    }
    existing.qty++;
  } else {
    if (!STATE.settings.allowNegativeStock && product.stock <= 0) {
      alert(`⚠️ Article en rupture de stock (${product.name})`);
      return;
    }
    STATE.cart.push({ product, qty: 1 });
  }
  updateFloatingCart();
}

function updateFloatingCart() {
  const bar = document.getElementById('floatingCartBar');
  const countSpan = document.getElementById('floatCartCount');
  const totalSpan = document.getElementById('floatCartTotal');

  const totalCount = STATE.cart.reduce((sum, i) => sum + i.qty, 0);
  const totalAmount = getCartTotal();

  if (totalCount > 0) {
    bar.style.display = 'flex';
    countSpan.textContent = totalCount;
    totalSpan.textContent = totalAmount.toLocaleString('fr-FR');
  } else {
    bar.style.display = 'none';
  }
}

function getCartSubtotal() {
  return STATE.cart.reduce((sum, i) => sum + (i.product.salePrice * i.qty), 0);
}

function getCartTotal() {
  let sub = getCartSubtotal();
  if (STATE.appliedPromo) {
    sub = sub * (1 - STATE.appliedPromo.discountPercent / 100);
  }
  if (STATE.settings.taxEnabled) {
    sub = sub * (1 + STATE.settings.taxRatePercent / 100);
  }
  return Math.round(sub);
}

function renderCartModal() {
  const list = document.getElementById('cartItemsList');
  list.innerHTML = '';

  STATE.cart.forEach((item, idx) => {
    const div = document.createElement('div');
    div.className = 'cart-item';
    div.innerHTML = `
      <div style="flex: 1;">
        <div style="font-weight: 600; font-size: 14px;">${item.product.name}</div>
        <div style="font-size: 12px; color: var(--text-muted);">${item.product.salePrice.toLocaleString('fr-FR')} CFA × ${item.qty} = ${(item.product.salePrice * item.qty).toLocaleString('fr-FR')} CFA</div>
      </div>
      <div class="qty-control">
        <button class="qty-btn btn-minus" data-idx="${idx}">-</button>
        <span style="font-weight: 700; min-width: 20px; text-align: center;">${item.qty}</span>
        <button class="qty-btn btn-plus" data-idx="${idx}">+</button>
      </div>
    `;
    list.appendChild(div);
  });

  list.querySelectorAll('.btn-minus').forEach(btn => {
    btn.addEventListener('click', () => {
      const i = parseInt(btn.dataset.idx);
      if (STATE.cart[i].qty > 1) {
        STATE.cart[i].qty--;
      } else {
        requestAdminPin("Supprimer cet article du panier", () => {
          STATE.cart.splice(i, 1);
          renderCartModal();
          updateFloatingCart();
        });
        return;
      }
      renderCartModal();
      updateFloatingCart();
    });
  });

  list.querySelectorAll('.btn-plus').forEach(btn => {
    btn.addEventListener('click', () => {
      const i = parseInt(btn.dataset.idx);
      addToCart(STATE.cart[i].product);
      renderCartModal();
    });
  });

  document.getElementById('cartSubtotalText').textContent = getCartSubtotal().toLocaleString('fr-FR') + ' CFA';
  if (STATE.settings.taxEnabled) {
    document.getElementById('cartTaxRow').style.display = 'flex';
    const taxVal = Math.round(getCartSubtotal() * (STATE.settings.taxRatePercent / 100));
    document.getElementById('cartTaxText').textContent = taxVal.toLocaleString('fr-FR') + ' CFA';
  } else {
    document.getElementById('cartTaxRow').style.display = 'none';
  }
  document.getElementById('cartTotalText').textContent = getCartTotal().toLocaleString('fr-FR') + ' CFA';
}

// ==========================================
// 5. BLUETOOTH THERMAL PRINTER DRIVER (Web Bluetooth ESC/POS)
// ==========================================
async function connectBluetoothPrinter() {
  if (!navigator.bluetooth) {
    alert("⚠️ Web Bluetooth n'est pas supporté directement par Safari classique. Sur iOS, utilisez l'application Bluefy ou connectez une imprimante Wi-Fi / AirPrint !");
    return;
  }
  try {
    const device = await navigator.bluetooth.requestDevice({
      acceptAllDevices: true,
      optionalServices: [
        '000018f0-0000-1000-8000-00805f9b34fb',
        'e7810a71-73ae-499d-8c15-faa9aef0c3f2',
        '49535343-fe7d-4ae5-8fa9-9fafd205e455'
      ]
    });
    const server = await device.gatt.connect();
    const services = await server.getPrimaryServices();
    for (const s of services) {
      const chars = await s.getCharacteristics();
      for (const c of chars) {
        if (c.properties.write || c.properties.writeWithoutResponse) {
          STATE.bleCharacteristic = c;
          STATE.bleDevice = device;
          alert(`✅ Imprimante Bluetooth connectée : ${device.name || 'Imprimante ESC/POS'}`);
          return;
        }
      }
    }
  } catch (err) {
    console.error(err);
  }
}

async function sendToPrinter(rawBytes) {
  if (STATE.bleCharacteristic) {
    try {
      const chunkSize = 100;
      for (let i = 0; i < rawBytes.length; i += chunkSize) {
        const chunk = rawBytes.slice(i, i + chunkSize);
        await STATE.bleCharacteristic.writeValue(chunk);
      }
    } catch (e) {
      console.error(e);
      window.print();
    }
  } else {
    // Fallback to iOS System Print
    window.print();
  }
}

// ==========================================
// 6. RECEIPT GENERATOR & PRINTING
// ==========================================
function removeAccents(str) {
  if (!str) return '';
  return String(str)
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/œ/g, "oe")
    .replace(/æ/g, "ae")
    .replace(/[^\x20-\x7E\n]/g, ""); // Keep pure ASCII for thermal print heads
}

function padLine(left, right, width, fill = ' ') {
  left = String(left || '');
  right = String(right || '');
  const maxLeft = width - right.length - 1;
  if (left.length > maxLeft) {
    left = left.substring(0, maxLeft);
  }
  const spaces = Math.max(1, width - left.length - right.length);
  return left + fill.repeat(spaces) + right;
}

function centerText(text, width) {
  text = String(text || '').trim();
  if (text.length >= width) return text.substring(0, width);
  const leftPad = Math.floor((width - text.length) / 2);
  const rightPad = width - text.length - leftPad;
  return ' '.repeat(leftPad) + text + ' '.repeat(rightPad);
}

function generateReceiptPlainText(ticket) {
  const width = (STATE.settings.printerWidth === "80") ? 42 : 32;
  const sep = '='.repeat(width);
  const dash = '-'.repeat(width);
  const dateStr = new Date(ticket.date).toLocaleString('fr-FR');

  let out = [];
  out.push(sep);
  out.push(centerText(removeAccents(STATE.settings.shopName || "SYPOS COMMERCE").toUpperCase(), width));
  if (STATE.settings.shopAddress) {
    out.push(centerText(removeAccents(STATE.settings.shopAddress), width));
  }
  if (STATE.settings.shopPhone) {
    out.push(centerText("Tel: " + removeAccents(STATE.settings.shopPhone), width));
  }
  out.push(sep);
  out.push(padLine("Ticket:", ticket.number, width));
  out.push(padLine("Date:", dateStr, width));
  out.push(padLine("Vendeur:", removeAccents(STATE.settings.sellerName || "Caisse"), width));
  out.push(dash);

  ticket.items.forEach(it => {
    const name = removeAccents(it.productName);
    const totalLine = (it.price * it.qty).toLocaleString('fr-FR') + ' F';
    const qtyLine = `${it.qty}x${it.price.toLocaleString('fr-FR')}`;
    
    // Check if item fits in one line with dots
    const leftText = `${name} (${qtyLine})`;
    if (leftText.length + totalLine.length + 2 <= width) {
      out.push(padLine(leftText, totalLine, width, '.'));
    } else {
      // Print name on first line, and qty + price on second line
      out.push(name.substring(0, width));
      out.push(padLine(`  ${qtyLine}`, totalLine, width, '.'));
    }
  });

  out.push(dash);
  out.push(padLine("TOTAL NET :", ticket.totalAmount.toLocaleString('fr-FR') + ' CFA', width));
  out.push(padLine("Reglement :", removeAccents((ticket.paymentMethod || 'ESPECES').toUpperCase()), width));

  if (ticket.cashReceived) {
    out.push(padLine("Recu :", ticket.cashReceived.toLocaleString('fr-FR') + ' CFA', width));
  }
  if (ticket.changeGiven) {
    out.push(padLine("Rendu :", ticket.changeGiven.toLocaleString('fr-FR') + ' CFA', width));
  }
  out.push(sep);

  if (STATE.settings.receiptFooter) {
    out.push(centerText(removeAccents(STATE.settings.receiptFooter), width));
  }
  if (STATE.settings.showPublisherSignature) {
    out.push(centerText(removeAccents(STATE.settings.publisherSignatureText || "SYPOS MOBILE 0758245530"), width));
  }
  
  out.push('\n\n'); // Line feeds for paper cut
  return out.join('\n');
}

function generateReceiptHtml(ticket) {
  const dateStr = new Date(ticket.date).toLocaleString('fr-FR');
  let itemsHtml = '';
  ticket.items.forEach(it => {
    itemsHtml += `
      <div class="receipt-row">
        <span>${it.productName} ×${it.qty}</span>
        <span>${(it.price * it.qty).toLocaleString('fr-FR')} CFA</span>
      </div>
    `;
  });

  return `
    <div class="receipt-center receipt-bold" style="font-size: 14px;">${STATE.settings.shopName.toUpperCase()}</div>
    <div class="receipt-center">${STATE.settings.shopAddress}</div>
    <div class="receipt-center">Tél: ${STATE.settings.shopPhone}</div>
    <div class="receipt-divider"></div>
    <div class="receipt-row"><span>Ticket N°:</span><span>${ticket.number}</span></div>
    <div class="receipt-row"><span>Date:</span><span>${dateStr}</span></div>
    <div class="receipt-row"><span>Vendeur:</span><span>${STATE.settings.sellerName}</span></div>
    <div class="receipt-divider"></div>
    ${itemsHtml}
    <div class="receipt-divider"></div>
    <div class="receipt-row receipt-bold" style="font-size: 14px;">
      <span>TOTAL NET :</span>
      <span>${ticket.totalAmount.toLocaleString('fr-FR')} CFA</span>
    </div>
    <div class="receipt-row"><span>Règlement :</span><span>${ticket.paymentMethod.toUpperCase()}</span></div>
    ${ticket.cashReceived ? `<div class="receipt-row"><span>Reçu :</span><span>${ticket.cashReceived.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    ${ticket.changeGiven ? `<div class="receipt-row"><span>Rendu :</span><span>${ticket.changeGiven.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    <div class="receipt-divider"></div>
    <div class="receipt-center" style="font-size: 11px;">${STATE.settings.receiptFooter}</div>
    ${STATE.settings.showPublisherSignature ? `<div class="receipt-center" style="font-size: 9px; color:#666; margin-top:6px;">${STATE.settings.publisherSignatureText}</div>` : ''}
  `;
}

// ==========================================
// 7. CRYPTOGRAPHIC LICENSE MANAGEMENT (RSA-2048)
// ==========================================
const RSA_PUBLIC_KEY_B64 = 
  "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAy4fzpl4lsQkScqSwHrAx" +
  "CiUEndXd1Kop9u/kKdGtxmvvJzxD15KN71TJ8ZVw/ds749Pz3yCdzlU7io5fDshu" +
  "VLYNVCXIpOJUtjGdtxVNCv9l0+bpAUKLZpPEHGgyXlDGbVE8G6bOQXXt57CoFdTQ" +
  "iGm7iBuQ2jcI6tW2Y0BZztforF57YX133ls9Eex5aM7pjzQcY31hrlLDKN3+1CD+" +
  "/XFSfPw5WfcLlOYm0x6FzFkFhG6s5qSNB1cfQ5yjScXvHGXoRa5Eo/MH4BXkU2vT" +
  "IZS5Y4+gqdc4T6drGyzQ2uUUtsII8zNSh0gRZAja452p4EuwpyrcgcopZG497gHp" +
  "5QIDAQAB";

function getDeviceId() {
  let devId = localStorage.getItem('sypos_device_id');
  if (!devId) {
    const rawFp = (navigator.userAgent || '') + '|' + screen.width + 'x' + screen.height + '|' + (navigator.hardwareConcurrency || 4) + '|' + Date.now();
    let hash = 0;
    for (let i = 0; i < rawFp.length; i++) {
      hash = ((hash << 5) - hash) + rawFp.charCodeAt(i);
      hash |= 0;
    }
    const hex1 = Math.abs(hash).toString(16).toUpperCase().padStart(4, '0').substring(0, 4);
    const hex2 = Math.floor(1000 + Math.random() * 9000).toString(16).toUpperCase().padStart(4, '0').substring(0, 4);
    devId = `SYPOS-DEV-${hex1}-${hex2}`;
    localStorage.setItem('sypos_device_id', devId);
  }
  return devId;
}

function base64UrlDecode(str) {
  let b64 = str.replace(/-/g, '+').replace(/_/g, '/');
  while (b64.length % 4) b64 += '=';
  return (typeof forge !== 'undefined') ? forge.util.decode64(b64) : atob(b64);
}

function validateLicenseToken(rawKey) {
  const token = (rawKey || '').trim().replace(/[`\s\r\n]/g, '');
  if (!token) return { isValid: false, message: "Veuillez saisir votre clé de licence." };
  if (!token.startsWith('SYP1.')) return { isValid: false, message: "Clé invalide (doit commencer par 'SYP1.')." };

  const parts = token.split('.');
  if (parts.length !== 3) return { isValid: false, message: "Structure de clé corrompue." };

  try {
    const payloadBytes = base64UrlDecode(parts[1]);
    const sigBytes = base64UrlDecode(parts[2]);

    if (typeof forge === 'undefined') {
      return { isValid: false, message: "Module cryptographique non chargé. Vérifiez votre connexion." };
    }

    const der = forge.util.decode64(RSA_PUBLIC_KEY_B64);
    const asn1 = forge.asn1.fromDer(der);
    const pubKey = forge.pki.publicKeyFromAsn1(asn1);

    const md = forge.md.sha256.create();
    md.update(payloadBytes, 'raw');
    const isSignatureValid = pubKey.verify(md.digest().bytes(), sigBytes);

    if (!isSignatureValid) {
      return { isValid: false, message: "Signature invalide : cette clé est falsifiée ou corrompue." };
    }

    const payload = JSON.parse(payloadBytes);
    const targetDevId = (payload.devId || '').trim().toUpperCase();
    const currentDevId = getDeviceId().trim().toUpperCase();

    if (targetDevId !== currentDevId) {
      return { isValid: false, message: `Clé prévue pour l'appareil (${targetDevId}), incompatible avec (${currentDevId}).` };
    }

    const now = Date.now();
    if (payload.exp && payload.exp > 0 && now > payload.exp) {
      const expDate = new Date(payload.exp).toLocaleDateString('fr-FR');
      return { isValid: false, message: `Cette licence a expiré le ${expDate}.` };
    }

    const planLabel = payload.plan === 'LIFETIME' ? 'Définitive (À Vie)' : (payload.plan === 'ANNUAL' ? 'Annuelle Pro' : 'Commerciale');

    return {
      isValid: true,
      licenseType: planLabel,
      expiryDate: payload.exp || 0,
      shopName: payload.shop || STATE.settings.shopName,
      message: `✅ Licence ${planLabel} activée avec succès !`
    };
  } catch (err) {
    return { isValid: false, message: "Erreur de validation : " + err.message };
  }
}

function isLicenseActive() {
  if (!STATE.settings.isLicensed) return false;
  if (STATE.settings.licenseExpiryDate > 0 && Date.now() > STATE.settings.licenseExpiryDate) {
    return false;
  }
  return true;
}

// ==========================================
// 8. ADMIN PIN SECURITY CHECK
// ==========================================
function requestAdminPin(actionDesc, onSuccess) {
  STATE.pendingPinAction = onSuccess;
  document.getElementById('pinActionDescription').textContent = actionDesc;
  document.getElementById('pinInputCode').value = '';
  document.getElementById('modalPin').classList.add('active');
}

// ==========================================
// 9. INITIALIZATION & EVENT LISTENERS
// ==========================================
document.addEventListener('DOMContentLoaded', () => {
  loadFromStorage();
  initTabs();
  renderPos();

  // License System Setup & Verification
  const currentDevId = getDeviceId();
  const licenseDevIdEl = document.getElementById('licenseDialogDevId');
  if (licenseDevIdEl) licenseDevIdEl.textContent = currentDevId;

  function showLicenseModal(isInitial = false) {
    const modal = document.getElementById('modalLicense');
    const closeBtn = document.getElementById('btnCloseLicenseModal');
    if (closeBtn) closeBtn.style.display = isInitial ? 'none' : 'block';
    const feedback = document.getElementById('licenseFeedbackMsg');
    if (feedback) feedback.style.display = 'none';
    modal.classList.add('active');
  }

  // Check License on Startup (Auto lock if unactivated)
  if (!isLicenseActive()) {
    showLicenseModal(true);
  }

  // Copy Device ID button
  document.getElementById('btnCopyDeviceId').addEventListener('click', () => {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(currentDevId).then(() => {
        alert("📋 ID Appareil copié dans le presse-papier :\n" + currentDevId);
      });
    } else {
      alert("Votre ID Appareil : " + currentDevId);
    }
  });

  // WhatsApp Order License button
  document.getElementById('btnWhatsAppLicense').addEventListener('click', () => {
    const phone = STATE.settings.shopPhone ? STATE.settings.shopPhone.replace(/[^0-9]/g, '') : "2250758245530";
    const msg = `Bonjour SYPOS, je souhaite activer la licence commerciale pour mon appareil :\n🆔 ID Appareil : *${currentDevId}*`;
    window.open(`https://api.whatsapp.com/send?phone=${phone}&text=${encodeURIComponent(msg)}`, '_blank');
  });

  // Validate License Button
  document.getElementById('btnValidateLicense').addEventListener('click', () => {
    const key = document.getElementById('inputLicenseKey').value;
    const res = validateLicenseToken(key);
    const feedback = document.getElementById('licenseFeedbackMsg');
    feedback.style.display = 'block';
    if (res.isValid) {
      feedback.style.background = '#e8f5e9';
      feedback.style.color = '#2e7d32';
      feedback.textContent = res.message;
      STATE.settings.isLicensed = true;
      STATE.settings.licenseKey = key.trim();
      STATE.settings.licenseType = res.licenseType;
      STATE.settings.licenseExpiryDate = res.expiryDate;
      saveToStorage();
      setTimeout(() => {
        document.getElementById('modalLicense').classList.remove('active');
        renderSettings();
      }, 1500);
    } else {
      feedback.style.background = '#ffebee';
      feedback.style.color = '#c62828';
      feedback.textContent = res.message;
    }
  });

  // Scan License QR Code button
  document.getElementById('btnScanLicenseQr').addEventListener('click', () => {
    document.getElementById('modalLicense').classList.remove('active');
    document.getElementById('modalScanner').classList.add('active');
    const qr = new Html5Qrcode("qr-reader");
    STATE.html5QrCode = qr;
    qr.start({ facingMode: "environment" }, { fps: 10, qrbox: { width: 250, height: 250 } }, (token) => {
      qr.stop().then(() => {
        document.getElementById('modalScanner').classList.remove('active');
        document.getElementById('inputLicenseKey').value = token;
        document.getElementById('modalLicense').classList.add('active');
        document.getElementById('btnValidateLicense').click();
      });
    }).catch(err => {
      alert("Impossible d'ouvrir la caméra : " + err);
      document.getElementById('modalScanner').classList.remove('active');
      document.getElementById('modalLicense').classList.add('active');
    });
  });

  // Open License Modal from Settings
  const btnOpenLicense = document.getElementById('btnOpenLicenseModal');
  if (btnOpenLicense) {
    btnOpenLicense.addEventListener('click', () => showLicenseModal(false));
  }

  // Search input live filter
  document.getElementById('posSearchInput').addEventListener('input', renderPos);

  // Floating cart click
  document.getElementById('floatingCartBar').addEventListener('click', () => {
    renderCartModal();
    document.getElementById('modalCart').classList.add('active');
  });

  // Modal Close buttons
  document.querySelectorAll('.close-modal').forEach(btn => {
    btn.addEventListener('click', () => {
      btn.closest('.modal-overlay').classList.remove('active');
    });
  });

  // Clear Cart
  document.getElementById('btnClearCartBtn').addEventListener('click', () => {
    requestAdminPin("Vider complètement le panier", () => {
      STATE.cart = [];
      STATE.appliedPromo = null;
      document.getElementById('modalCart').classList.remove('active');
      updateFloatingCart();
      renderPos();
    });
  });

  // Checkout button
  document.getElementById('btnGoToCheckout').addEventListener('click', () => {
    document.getElementById('modalCart').classList.remove('active');
    const total = getCartTotal();
    document.getElementById('payAmountDue').textContent = total.toLocaleString('fr-FR') + ' CFA';
    document.getElementById('payCashReceivedInput').value = '';
    document.getElementById('payChangeDueBox').style.display = 'none';

    // Populate customers
    const custSelect = document.getElementById('payCustomerSelect');
    custSelect.innerHTML = '<option value="">Client Comptant</option>';
    STATE.customers.forEach(c => {
      custSelect.innerHTML += `<option value="${c.id}">${c.name}</option>`;
    });

    document.getElementById('modalPayment').classList.add('active');
  });

  // Payment Method change
  document.getElementById('payPaymentMethod').addEventListener('change', (e) => {
    const isCash = e.target.value === 'cash';
    const isCredit = e.target.value === 'credit';
    document.getElementById('payCashReceivedGroup').style.display = isCash ? 'block' : 'none';
    document.getElementById('payCustomerGroup').style.display = isCredit ? 'block' : 'none';
  });

  // Cash Received live calculation
  document.getElementById('payCashReceivedInput').addEventListener('input', (e) => {
    const rec = parseFloat(e.target.value) || 0;
    const total = getCartTotal();
    const box = document.getElementById('payChangeDueBox');
    if (rec >= total && total > 0) {
      box.style.display = 'block';
      document.getElementById('payChangeDueAmount').textContent = (rec - total).toLocaleString('fr-FR') + ' CFA';
    } else {
      box.style.display = 'none';
    }
  });

  // Confirm Payment & Record Sale
  document.getElementById('btnConfirmPayment').addEventListener('click', () => {
    const method = document.getElementById('payPaymentMethod').value;
    const total = getCartTotal();
    const rec = parseFloat(document.getElementById('payCashReceivedInput').value) || total;
    const change = Math.max(0, rec - total);

    const ticketNumber = "TK-" + String(STATE.tickets.length + 1).padStart(5, '0');
    const ticket = {
      id: "t_" + Date.now(),
      number: ticketNumber,
      date: new Date().toISOString(),
      items: STATE.cart.map(i => ({
        productId: i.product.id,
        productName: i.product.name,
        qty: i.qty,
        price: i.product.salePrice,
        costPrice: i.product.costPrice
      })),
      totalAmount: total,
      paymentMethod: method,
      cashReceived: method === 'cash' ? rec : null,
      changeGiven: method === 'cash' ? change : null,
      status: 'completed'
    };

    // Deduct stocks
    STATE.cart.forEach(i => {
      const prod = STATE.products.find(p => p.id === i.product.id);
      if (prod) prod.stock -= i.qty;
    });

    STATE.tickets.unshift(ticket);
    STATE.cart = [];
    STATE.appliedPromo = null;
    saveToStorage();

    document.getElementById('modalPayment').classList.remove('active');
    updateFloatingCart();
    renderPos();

    // Show Receipt
    document.getElementById('receiptPaperContent').innerHTML = generateReceiptHtml(ticket);
    document.getElementById('modalReceipt').classList.add('active');
  });

  // Print via Thermer BLE App (iOS Deep Link & Fallback)
  function sendToThermerApp(ticket) {
    if (!ticket) return;
    const plainText = generateReceiptPlainText(ticket);

    // 1. Copy to clipboard as quick fallback
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(plainText).catch(() => {});
    }

    // 2. Build structured PrintEntry JSON for Thermer
    const entries = [
      { type: "text", text: (STATE.settings.shopName || "SYPOS MOBILE").toUpperCase(), alignment: "center", bold: true, size: 2 },
      { type: "text", text: STATE.settings.shopAddress || "", alignment: "center" },
      { type: "text", text: "Tel: " + (STATE.settings.shopPhone || ""), alignment: "center" },
      { type: "line" },
      { type: "text", text: "Ticket: " + ticket.number, alignment: "left", bold: true },
      { type: "text", text: "Date: " + new Date(ticket.date).toLocaleString('fr-FR'), alignment: "left" },
      { type: "text", text: "Vendeur: " + (STATE.settings.sellerName || "Caisse"), alignment: "left" },
      { type: "line" }
    ];

    ticket.items.forEach(it => {
      const lineLeft = `${it.productName} x${it.qty}`;
      const lineRight = `${(it.price * it.qty).toLocaleString('fr-FR')} CFA`;
      entries.push({
        type: "text",
        text: `${lineLeft.padEnd(20, ' ')} ${lineRight}`,
        alignment: "left"
      });
    });

    entries.push(
      { type: "line" },
      { type: "text", text: `TOTAL: ${ticket.totalAmount.toLocaleString('fr-FR')} CFA`, alignment: "right", bold: true, size: 2 },
      { type: "text", text: `Reglement: ${(ticket.paymentMethod || 'ESPECES').toUpperCase()}`, alignment: "left" },
      { type: "line" },
      { type: "text", text: STATE.settings.receiptFooter || "Merci de votre visite !", alignment: "center" }
    );

    if (STATE.settings.showPublisherSignature) {
      entries.push({
        type: "text",
        text: STATE.settings.publisherSignatureText || "Sypos Mobile - Caisse Intelligente",
        alignment: "center"
      });
    }

    const payload = encodeURIComponent(JSON.stringify(entries));
    const thermerUrl = `thermer://print?data=${payload}`;

    // Try to open Thermer
    window.location.href = thermerUrl;

    // Provide user feedback
    setTimeout(() => {
      if (document.hidden) return; // App opened
      // If still on screen, show friendly prompt
      console.log("Deep link triggered for Thermer");
    }, 1200);
  }

  // Button Thermer in Receipt Modal
  document.getElementById('btnPrintThermerBtn').addEventListener('click', () => {
    if (STATE.tickets.length === 0) return;
    sendToThermerApp(STATE.tickets[0]);
  });

  // Test Thermer Button in Settings
  const btnTestThermer = document.getElementById('btnTestThermerPrint');
  if (btnTestThermer) {
    btnTestThermer.addEventListener('click', () => {
      const dummyTicket = {
        number: 'TEST-' + Math.floor(1000 + Math.random() * 9000),
        date: Date.now(),
        items: [
          { productName: 'Article Test 1', qty: 1, price: 1500 },
          { productName: 'Article Test 2', qty: 2, price: 2500 }
        ],
        totalAmount: 6500,
        paymentMethod: 'cash'
      };
      sendToThermerApp(dummyTicket);
    });
  }

  // Print Receipt Button (Standard / PDF)
  document.getElementById('btnPrintReceiptBtn').addEventListener('click', () => {
    window.print();
  });

  // WhatsApp Share Receipt
  document.getElementById('btnShareWhatsAppBtn').addEventListener('click', () => {
    if (STATE.tickets.length === 0) return;
    const t = STATE.tickets[0];
    let text = `*${STATE.settings.shopName}*\nTicket N°: ${t.number}\nTotal: ${t.totalAmount} CFA\nMerci de votre confiance !`;
    window.open(`https://api.whatsapp.com/send?text=${encodeURIComponent(text)}`, '_blank');
  });

  // Admin PIN submission
  document.getElementById('btnSubmitPin').addEventListener('click', () => {
    const entered = document.getElementById('pinInputCode').value;
    if (entered === STATE.settings.adminPin) {
      document.getElementById('modalPin').classList.remove('active');
      if (STATE.pendingPinAction) {
        STATE.pendingPinAction();
        STATE.pendingPinAction = null;
      }
    } else {
      alert("❌ Code PIN incorrect !");
    }
  });

  // Bluetooth button in header (Android / Chrome Web Bluetooth)
  document.getElementById('btnHeaderBle').addEventListener('click', connectBluetoothPrinter);

  // Settings Save
  document.getElementById('btnSaveSettings').addEventListener('click', () => {
    STATE.settings.shopName = document.getElementById('setShopName').value;
    STATE.settings.shopAddress = document.getElementById('setShopAddress').value;
    STATE.settings.shopPhone = document.getElementById('setShopPhone').value;
    STATE.settings.sellerName = document.getElementById('setSellerName').value;
    STATE.settings.receiptFooter = document.getElementById('setReceiptFooter').value;
    STATE.settings.showPublisherSignature = document.getElementById('setShowSignature').checked;
    STATE.settings.taxEnabled = document.getElementById('setTaxEnabled').checked;
    STATE.settings.adminPin = document.getElementById('setAdminPin').value || "1234";
    STATE.settings.printerWidth = document.getElementById('setPrinterWidth').value || "58";
    saveToStorage();
    alert("✅ Paramètres enregistrés avec succès !");
  });

  // Print Label in Tools Tab
  const btnPrintLabel = document.getElementById('btnPrintLabelNow');
  if (btnPrintLabel) {
    btnPrintLabel.addEventListener('click', () => {
      const name = document.getElementById('toolNameInput').value || 'Article';
      const price = document.getElementById('toolPriceInput').value || '0';
      const barcode = document.getElementById('toolBarcodeInput').value || '';
      
      const labelEntries = [
        { type: "text", text: (STATE.settings.shopName || "SYPOS").toUpperCase(), alignment: "center", bold: true },
        { type: "text", text: name, alignment: "center", bold: true, size: 2 },
        { type: "text", text: price + " CFA", alignment: "center", bold: true, size: 2 },
        { type: "line" }
      ];
      if (barcode) {
        labelEntries.push({ type: "text", text: "Code: " + barcode, alignment: "center" });
      }

      const payload = encodeURIComponent(JSON.stringify(labelEntries));
      window.location.href = `thermer://print?data=${payload}`;
    });
  }

  // Service Worker Registration for PWA Offline
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./service-worker.js').catch(console.error);
  }
});

// Other Tabs Rendering functions
function renderProducts() {
  const container = document.getElementById('productsListContainer');
  container.innerHTML = '';
  STATE.products.forEach(p => {
    const card = document.createElement('div');
    card.className = 'card-group';
    card.style.display = 'flex';
    card.style.justifyContent = 'space-between';
    card.style.alignItems = 'center';
    card.innerHTML = `
      <div>
        <div style="font-weight: 700; font-size: 15px;">${p.name}</div>
        <div style="font-size: 12px; color: var(--text-muted);">Stock : <strong>${p.stock}</strong> | Code : ${p.barcode || 'Aucun'}</div>
        <div style="font-size: 14px; font-weight: 700; color: var(--primary); margin-top: 2px;">${p.salePrice.toLocaleString('fr-FR')} CFA</div>
      </div>
      <button class="icon-btn del-prod-btn" data-id="${p.id}" style="color: var(--danger);">🗑️</button>
    `;
    card.querySelector('.del-prod-btn').addEventListener('click', () => {
      requestAdminPin(`Supprimer le produit ${p.name}`, () => {
        STATE.products = STATE.products.filter(x => x.id !== p.id);
        saveToStorage();
        renderProducts();
        renderPos();
      });
    });
    container.appendChild(card);
  });
}

function renderHistory() {
  const container = document.getElementById('historyListContainer');
  container.innerHTML = '';
  let totalPeriod = 0;

  STATE.tickets.forEach(t => {
    totalPeriod += t.totalAmount;
    const card = document.createElement('div');
    card.className = 'card-group';
    card.style.marginBottom = '10px';
    card.innerHTML = `
      <div style="display: flex; justify-content: space-between; align-items: center;">
        <div>
          <div style="font-weight: 700; font-size: 14px;">${t.number}</div>
          <div style="font-size: 11px; color: var(--text-muted);">${new Date(t.date).toLocaleString('fr-FR')}</div>
        </div>
        <div style="font-size: 16px; font-weight: 700; color: var(--primary);">${t.totalAmount.toLocaleString('fr-FR')} CFA</div>
      </div>
    `;
    card.addEventListener('click', () => {
      document.getElementById('receiptPaperContent').innerHTML = generateReceiptHtml(t);
      document.getElementById('modalReceipt').classList.add('active');
    });
    container.appendChild(card);
  });

  document.getElementById('historyTotalSales').textContent = totalPeriod.toLocaleString('fr-FR') + ' CFA';
}

function renderReports() {
  const rev = STATE.tickets.reduce((sum, t) => sum + t.totalAmount, 0);
  const stockVal = STATE.products.reduce((sum, p) => sum + (p.stock * p.salePrice), 0);
  document.getElementById('reportRevenue').textContent = rev.toLocaleString('fr-FR') + ' CFA';
  document.getElementById('reportProfit').textContent = Math.round(rev * 0.25).toLocaleString('fr-FR') + ' CFA';
  document.getElementById('reportStockValue').textContent = stockVal.toLocaleString('fr-FR') + ' CFA';
}

function renderCustomers() {
  const container = document.getElementById('customersListContainer');
  container.innerHTML = '';
  let totalDebt = 0;
  STATE.customers.forEach(c => {
    totalDebt += c.debt;
    const card = document.createElement('div');
    card.className = 'card-group';
    card.style.display = 'flex';
    card.style.justifyContent = 'space-between';
    card.style.alignItems = 'center';
    card.innerHTML = `
      <div>
        <div style="font-weight: 700; font-size: 15px;">${c.name}</div>
        <div style="font-size: 12px; color: var(--text-muted);">${c.phone || 'Pas de numéro'}</div>
      </div>
      <div style="text-align: right;">
        <div style="font-size: 11px; color: var(--text-muted);">Dette</div>
        <div style="font-size: 15px; font-weight: 700; color: ${c.debt > 0 ? 'var(--danger)' : 'var(--success)'};">${c.debt.toLocaleString('fr-FR')} CFA</div>
      </div>
    `;
    container.appendChild(card);
  });
  document.getElementById('customerTotalDebt').textContent = totalDebt.toLocaleString('fr-FR') + ' CFA';
}

function renderTools() {
  const select = document.getElementById('toolProductSelect');
  select.innerHTML = '<option value="">-- Personnalisé --</option>';
  STATE.products.forEach(p => {
    select.innerHTML += `<option value="${p.id}">${p.name} (${p.salePrice} CFA)</option>`;
  });
  select.onchange = (e) => {
    const prod = STATE.products.find(p => p.id === e.target.value);
    if (prod) {
      document.getElementById('toolNameInput').value = prod.name;
      document.getElementById('toolPriceInput').value = prod.salePrice;
      document.getElementById('toolBarcodeInput').value = prod.barcode || '';
      document.getElementById('lblName').textContent = prod.name;
      document.getElementById('lblPrice').textContent = prod.salePrice + ' CFA';
      document.getElementById('lblSerial').textContent = prod.barcode || '';
    }
  };
}

function renderSettings() {
  document.getElementById('setShopName').value = STATE.settings.shopName;
  document.getElementById('setShopAddress').value = STATE.settings.shopAddress;
  document.getElementById('setShopPhone').value = STATE.settings.shopPhone;
  document.getElementById('setSellerName').value = STATE.settings.sellerName;
  document.getElementById('setReceiptFooter').value = STATE.settings.receiptFooter;
  document.getElementById('setShowSignature').checked = STATE.settings.showPublisherSignature;
  document.getElementById('setTaxEnabled').checked = STATE.settings.taxEnabled;
  document.getElementById('setAdminPin').value = STATE.settings.adminPin;
  if (document.getElementById('setPrinterWidth')) {
    document.getElementById('setPrinterWidth').value = STATE.settings.printerWidth || "58";
  }

  // Update License Status Card
  const devIdEl = document.getElementById('settingsDevId');
  if (devIdEl) devIdEl.textContent = getDeviceId();

  const badge = document.getElementById('settingsLicenseStatusBadge');
  if (badge) {
    if (isLicenseActive()) {
      const exp = STATE.settings.licenseExpiryDate ? ` (${new Date(STATE.settings.licenseExpiryDate).toLocaleDateString('fr-FR')})` : ' (À vie)';
      badge.textContent = `✅ Active - ${STATE.settings.licenseType || 'Commerciale'}${exp}`;
      badge.style.background = '#e8f5e9';
      badge.style.color = '#2e7d32';
    } else {
      badge.textContent = '❌ Non Activée / Expirée';
      badge.style.background = '#ffebee';
      badge.style.color = '#c62828';
    }
  }
}
