package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;

public class OPKomplikationVsLookup {

  private static final HashMap<String, String> lookup = new HashMap<>();

  static {
    lookup.put("N", "Nein");
    lookup.put("U", "Unbekannt");
    lookup.put("ABD", "Abszeß in einem Drainagekanal");
    lookup.put("ABS", "Abszeß, intraabdominaler oder intrathorakaler");
    lookup.put("ASF", "Abszeß, subfaszialer");
    lookup.put("ANI", "Akute Niereninsuffizienz");
    lookup.put("AEP", "Alkoholentzugspsychose");
    lookup.put("ALR", "Allergische Reaktion ohne Schocksymptomatik");
    lookup.put("ANS", "Anaphylaktischer Schock");
    lookup.put("AEE", "Anastomoseninsuffizienz einer Enterostomie");
    lookup.put("API", "Apoplektischer Insult");
    lookup.put("BIF", "Biliäre Fistel");
    lookup.put("BOG", "Blutung, obere gastrointestinale (z.B \"Stressulkus\")");
    lookup.put("BOE", "Bolusverlegung eines Endotubus");
    lookup.put("BSI", "Bronchusstumpfinsuffizienz");
    lookup.put("CHI", "Cholangitis");
    lookup.put("DAI", "Darmanastomoseinsuffizienz");
    lookup.put("DPS", "Darmpassagestörungen (z.B. protrahierte Atonie, Subileus, Ileus)");
    lookup.put("DIC", "Disseminierte intravasale Koagulopathie");
    lookup.put("DEP", "Drogenentzugspsychose");
    lookup.put("DLU", "Druck- und Lagerungsschäden, z.B. Dekubitalulzera");
    lookup.put("DSI", "Duodenalstumpfinsuffizienz");
    lookup.put("ENF", "Enterale Fistel");
    lookup.put("GER", "Gerinnungsstörung");
    lookup.put("HEM", "Hämatemesis");
    lookup.put("HUR", "Hämaturie");
    lookup.put("HAE", "Hämorrhagischer Schock");
    lookup.put("HFI", "Harnfistel");
    lookup.put("HNK", "Hautnekrose im Operationsbereich");
    lookup.put("HZI", "Herzinsuffizienz");
    lookup.put("HRS", "Herzrhythmusstörungen");
    lookup.put("HNA", "Hirnnervenausfälle");
    lookup.put("HOP", "Hirnorganisches Psychosyndrom (z.B. \"Durchgangssyndrom\")");
    lookup.put("HYB", "Hyperbilirubinämie");
    lookup.put("HYF", "Hypopharynxfistel");
    lookup.put("IFV", "Ileofemorale Venenthrombose");
    lookup.put("KAS", "Kardiogener Schock");
    lookup.put("KES", "Komplikationen einer Stomaanlage");
    lookup.put(
        "KIM",
        "Komplikation eines Implantates (Gefäßprothese, Totalendoprothese, Katheter), z.B. Dislokation");
    lookup.put("KRA", "Krampfanfall");
    lookup.put("KDS", "Kurzdarmsyndrom");
    lookup.put("LEV", "Leberversagen");
    lookup.put("LOE", "Lungenödem");
    lookup.put("LYF", "Lymphfistel");
    lookup.put("LYE", "Lymphozele");
    lookup.put("MES", "Magenentleerungsstörung");
    lookup.put("MIL", "Mechanischer Ileus");
    lookup.put("MED", "Mediastinitis");
    lookup.put("MAT", "Mesenterialarterien- oder -venenthrombose");
    lookup.put("MYI", "Myokardinfarkt");
    lookup.put("RNB", "Nachblutung, revisionsbedürftig, anderweitig nicht erwähnt");
    lookup.put("NAB", "Nachblutung, nicht revisionsbedürftig, anderweitig nicht erwähnt");
    lookup.put("NIN", "Nahtinsuffizienz, anderweitig nicht erwähnt");
    lookup.put("OES", "Ösophagitis");
    lookup.put("OSM", "Osteitis, Osteomyelitis");
    lookup.put("PAF", "Pankreasfistel");
    lookup.put("PIT", "Pankreatitis");
    lookup.put("PAB", "Peranale Blutung");
    lookup.put("PPA", "Periphere Parese");
    lookup.put("PAV", "Peripherer arterieller Verschluß (Embolie, Thrombose)");
    lookup.put("PER", "Peritonitis");
    lookup.put("PLB", "Platzbauch");
    lookup.put("PEY", "Pleuraempyem");
    lookup.put("PLE", "Pleuraerguß");
    lookup.put("PMN", "Pneumonie");
    lookup.put("PNT", "Pneumothorax");
    lookup.put("PDA", "Protrahierte Darmatonie (paralytischer Ileus)");
    lookup.put("PAE", "Pulmonalarterienembolie");
    lookup.put("RPA", "Rekurrensparese");
    lookup.put("RIN", "Respiratorische Insuffizienz");
    lookup.put("SKI", "Septische Komplikation eines Implantates");
    lookup.put("SES", "Septischer Schock");
    lookup.put("SFH", "Störungen des Flüssigkeits-, Elektrolyt- und Säurebasenhaushaltes");
    lookup.put("SON", "Sonstige Komplikation");
    lookup.put("STK", "Stomakomplikation (z.B. Blutung, Nekrose, Stenose)");
    lookup.put("TZP", "Thrombozytopenie");
    lookup.put(
        "TIA",
        "TIA (transitorische ischämische Attacke) oder RIND (reversibles ischämisches neurologisches Defizit)");
    lookup.put("TRZ", "Transfusionszwischenfall");
    lookup.put("WUH", "Wundhämatom (konservativ therapiert)");
    lookup.put("WSS", "Wundheilungsstörung, subkutane");
  }

  public static String lookupDisplay(String code) {
    return lookup.get(code);
  }
}
