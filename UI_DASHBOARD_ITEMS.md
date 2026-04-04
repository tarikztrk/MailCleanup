# Dashboard UI Backlog

Bu dosya sadece dashboard (abonelik listesi) ekranının UI/UX iyileştirmelerini içerir.
Öncelik sırası yüksekten düşüğe verilmiştir.

## 1) Üstteki arama ikiliği / tutarsızlık
- Durum: Eksik
- Risk: Yüksek
- Neden önemli:
  Dashboard üstündeki arama ikonu ile gerçek arama akışı ayrık davranıyor.
- Ne yapılmalı:
  Tek bir arama girişi akışı kullanılmalı ve ikon bu akışı tetiklemeli.
- Kabul kriteri:
  Kullanıcı üstteki arama ikonuna basınca anında arama yapabilmeli.

## 2) Sort + filter chip'ler işlevsel değil
- Durum: Eksik
- Risk: Yüksek
- Neden önemli:
  Görselde etkileşimli görünen kontrol çalışmadığında güven düşer.
- Ne yapılmalı:
  Sort kontrolü ve All/Newsletters/Promotions chipleri veri üstünde gerçek etki üretmeli.
- Kabul kriteri:
  Chip değişince liste değişmeli; sort seçimi görsel ve davranış olarak yansımalı.

## 3) Bottom nav ve FAB davranışı tanımsız
- Durum: Eksik
- Risk: Yüksek
- Neden önemli:
  Tıklanabilir görünen öğeler aksiyon üretmeyince broken hissi verir.
- Ne yapılmalı:
  Alt menü sekmeleri ve FAB için en azından temel navigasyon/aksiyon akışı bağlanmalı.
- Kabul kriteri:
  Her öğe tıklamada net geri bildirim veya ekran değişimi vermeli.

## 4) Dashboard metrikleri statik
- Durum: Eksik
- Risk: Orta-Yüksek
- Neden önemli:
  Haftalık inflow / saved minutes gibi değerler sabit kalınca ürün algısı zayıflar.
- Ne yapılmalı:
  Metrikler gerçek data üzerinden hesaplanmalı veya açıkça "demo" olarak etiketlenmeli.
- Kabul kriteri:
  Metrikler liste/dönem değiştikçe güncellenmeli.

## 5) Kart içi open-rate gerçek değil
- Durum: Eksik
- Risk: Orta-Yüksek
- Neden önemli:
  Hash tabanlı oran kullanımı güvenilir analitik izlenimi vermez.
- Ne yapılmalı:
  Gerçek metrik üretimi gelene kadar bu alan kaldırılmalı ya da "estimated" olarak işaretlenmeli.
- Kabul kriteri:
  Gösterilen oranlar açıklanabilir bir kaynaktan gelmeli.

## 6) İlk yükleme deneyimi zayıf (skeleton yok)
- Durum: Eksik
- Risk: Orta
- Neden önemli:
  İlk açılışta boş alan görmek "çalışmıyor" algısı yaratır.
- Ne yapılmalı:
  Refresh sırasında liste boşsa merkezi loading/skeleton gösterilmeli.
- Kabul kriteri:
  İlk yüklemede kullanıcı sürekli bir "yükleniyor" geri bildirimi görmeli.

## 7) Hata ve boş durum CTA eksik
- Durum: Eksik
- Risk: Orta
- Neden önemli:
  Snackbar kaybolduğunda kullanıcı ne yapacağını bilemeyebilir.
- Ne yapılmalı:
  Kalıcı hata paneli + "Tekrar dene" ve boş durumda yönlendirici CTA eklenmeli.
- Kabul kriteri:
  Hata sonrası kullanıcı tek tıkla yeniden deneme yapabilmeli.

## 8) Erişilebilirlik iyileştirmeleri
- Durum: Kısmi
- Risk: Orta
- Neden önemli:
  Küçük fontlar ve yetersiz contentDescription erişilebilirliği düşürür.
- Ne yapılmalı:
  Kritik metin boyutları artırılmalı, icon butonlara doğru açıklamalar verilmeli.
- Kabul kriteri:
  TalkBack ve küçük ekranlarda okunabilirlik kabul edilebilir olmalı.

## 9) Satır aksiyon affordance belirsiz (Keep)
- Durum: Eksik
- Risk: Orta
- Neden önemli:
  Keep aksiyonu görünmeyince kullanıcı yalnızca unsubscribe akışını fark eder.
- Ne yapılmalı:
  Her satırda keep aksiyonu görünür, tutarlı ve erişilebilir hale getirilmeli.
- Kabul kriteri:
  Kullanıcı ek yardım olmadan keep ve unsubscribe aksiyonlarını ayırt edebilmeli.

## 10) Design parity mikro farklar
- Durum: Kısmi
- Risk: Düşük-Orta
- Neden önemli:
  Spacing/typography farkları ekranın "birebir" hissini bozar.
- Ne yapılmalı:
  Referans tasarıma göre margin, radius, tipografi ve tonlar tek tek hizalanmalı.
- Kabul kriteri:
  Screenshot karşılaştırmasında belirgin sapmalar kapanmış olmalı.
