ALTER SESSION SET CONTAINER = FREEPDB1;
create table DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG
(
    ID                           NUMBER(10),
    ERKRANKUNG_ID                NUMBER(10),
    ART                          NUMBER(10) not null,
    ZU_MELDEN                    NUMBER(5)  not null,
    LKR_STATUS                   VARCHAR2(2 char),
    HINWEIS                      CLOB,
    MELDER_OE                    NUMBER(10),
    PROZEDURDATUM                TIMESTAMP(6),
    TAN                          VARCHAR2(30 char),
    DURCHFUEHRENDE_EINRICHTUNG   NUMBER(10),
    DURCHFUEHRENDE_FACHABTEILUNG NUMBER(10),
    RELEVANT_FORM_VALUES         CLOB,
    XML_VALIDATION               CLOB,
    EXTERN                       NUMBER(5)  not null,
    HAUPT_LKR_MELDUNGSTEIL       NUMBER(10),
    XML_DATEN                    CLOB,
    PATIENT_ID                   NUMBER(10),
    MELDER_ID                    VARCHAR2(30 char)
)
    /


INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG (ID, ERKRANKUNG_ID, ART, ZU_MELDEN, LKR_STATUS, HINWEIS, MELDER_OE,
                                                  PROZEDURDATUM, TAN, DURCHFUEHRENDE_EINRICHTUNG,
                                                  DURCHFUEHRENDE_FACHABTEILUNG, RELEVANT_FORM_VALUES, XML_VALIDATION,
                                                  EXTERN, HAUPT_LKR_MELDUNGSTEIL, XML_DATEN, PATIENT_ID, MELDER_ID)
VALUES (1, 1, 1, 0, '0', null, 1, TO_TIMESTAMP('2021-01-07 00:00:00.000000', 'YYYY-MM-DD HH24:MI:SS.FF6'), 'FK0000001',
    1, 21, '{"1":{"231":"C50.2","1886":"Mamma-Ca links","233":"L","1226":"0","234":"7","1696":"07.01.2021","2059":"Universit„tsklinikum Erlangen, FK","228":"C50.2","2030":"8520/3","2060":"M"},"65":{"2070":"c","2087":"","2072":"c","2071":"c","2119":"nein","2118":"nein","2007":"07.01.2021","2117":"nein","1897":"0","1434":"cT1 cN1 cM0","1894":"1","1906":"nein","8327":"nein","1904":"1","1922":"IIA"},"71":{"1745":"07.01.2021","1662":"0","1233":"0","2069":"nein","1265":"3"},"82":{"1755":"07.01.2021","1963":"12","2116":"nein","1962":"IRS","2115":"nein","1960":"12","2026":"5,0","457":"8520/3","458":"1","317":"12100324","1959":"IRS","1923":"nein"},"74248":{"1753":"02.08.2021","2001":"D","2032":"Gr”áenregredientes Mammakarzinom links auf 12 Uhr. Lymphknoten unklarer Dignit„t beidseits axill„r.\nExulzeriertes Mammakarzinom rechts, laut Patientin weiter gr”áenregredient","1748":"02.08.2021","1614":"U"},"OS_EINWILLIGUNG":{"OS_EINWILLIGUNG_MELDEBEGRUENDUNG":"I"}}',
    null, 0, 1, to_clob( '<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.2.1">
    <Absender Absender_ID="UKER_ONKOSTAR" Software_ID="ONKOSTAR" Installations_ID="2.9.8">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1015260170">
                <KrankenversichertenNr>T411337835</KrankenversichertenNr>
                <KrankenkassenNr>108310400</KrankenkassenNr>
                <Patienten_Nachname>Vader</Patienten_Nachname>
                <Patienten_Titel/>
                <Patienten_Namenszusatz/>
                <Patienten_Vornamen>Darth</Patienten_Vornamen>
                <Patienten_Geburtsname/>
                <Patienten_Geschlecht>W</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>06.06.1966</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Teststr. 132</Patienten_Strasse>
                        <Patienten_Hausnummer/>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>96111</Patienten_PLZ>
                        <Patienten_Ort>Testort</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
            <Menge_Meldung>
                <Meldung Meldung_ID="FK0000001" Melder_ID="FK">
                    <Meldedatum>16.12.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>diagnose</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>C50.2</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>07.01.2021</Diagnosedatum>
                        <Seitenlokalisation>L</Seitenlokalisation>
                    </Tumorzuordnung>
                    <Diagnose Tumor_ID="1">
                        <Primaertumor_ICD_Code>C50.2</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Primaertumor_Topographie_ICD_O>C50.2</Primaertumor_Topographie_ICD_O>
                        <Primaertumor_Topographie_ICD_O_Version>32</Primaertumor_Topographie_ICD_O_Version>
                        <Diagnosedatum>07.01.2021</Diagnosedatum>
                        <Diagnosesicherung>7</Diagnosesicherung>
                        <Seitenlokalisation>L</Seitenlokalisation>
                        <Menge_Histologie>
                            <Histologie Histologie_ID="82">
                                <Tumor_Histologiedatum>07.01.2021</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>12100333</Histologie_EinsendeNr>
                                <Morphologie_Code>8520/3</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Grading>1</Grading>') ||
                        to_clob('</Histologie>
                        </Menge_Histologie>
                        <cTNM TNM_ID="65">
                            <TNM_Datum>07.01.2021</TNM_Datum>
                            <TNM_Version>8</TNM_Version>
                            <TNM_c_p_u_Praefix_T>c</TNM_c_p_u_Praefix_T>
                            <TNM_T>1</TNM_T>
                            <TNM_c_p_u_Praefix_N>c</TNM_c_p_u_Praefix_N>
                            <TNM_N>1</TNM_N>
                            <TNM_c_p_u_Praefix_M>c</TNM_c_p_u_Praefix_M>
                            <TNM_M>0</TNM_M>
                        </cTNM>
                        <Menge_Weitere_Klassifikation>
                            <Weitere_Klassifikation>
                                <Datum>07.01.2021</Datum>
                                <Name>UICC</Name>
                                <Stadium>IIA</Stadium>
                            </Weitere_Klassifikation>
                        </Menge_Weitere_Klassifikation>
                        <Modul_Mamma>
                            <Praetherapeutischer_Menopausenstatus>3</Praetherapeutischer_Menopausenstatus>
                            <HormonrezeptorStatus_Oestrogen>P</HormonrezeptorStatus_Oestrogen>
                            <HormonrezeptorStatus_Progesteron>P</HormonrezeptorStatus_Progesteron>
                            <Her2neuStatus>N</Her2neuStatus>
                        </Modul_Mamma>
                        <Allgemeiner_Leistungszustand>U</Allgemeiner_Leistungszustand>
                    </Diagnose>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="FK">
            <Meldende_Stelle>FK</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>
'), null, null);

INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG (ID, ERKRANKUNG_ID, ART, ZU_MELDEN, LKR_STATUS, HINWEIS, MELDER_OE,
                                                  PROZEDURDATUM, TAN, DURCHFUEHRENDE_EINRICHTUNG,
                                                  DURCHFUEHRENDE_FACHABTEILUNG, RELEVANT_FORM_VALUES, XML_VALIDATION,
                                                  EXTERN, HAUPT_LKR_MELDUNGSTEIL, XML_DATEN, PATIENT_ID, MELDER_ID)
VALUES (2, 2, 1, 0, '0', null, 1, TO_TIMESTAMP('2021-01-07 00:00:00.000000', 'YYYY-MM-DD HH24:MI:SS.FF6'), 'FK0000002',
        1, 21, '{"5":{"231":"C50.9","233":"R","1226":"0","234":"7","1696":"07.01.2021","2059":"Universit„tsklinikum Erlangen, FK","228":"C50.9","2030":"8500/3","2060":"M"},"59":{"2070":"c","2087":"","2072":"c","2071":"c","2119":"nein","2118":"nein","2007":"12.01.2021","2117":"nein","1897":"0","1434":"cT4c cN1 cM0","1894":"1","1906":"nein","8327":"nein","1904":"4c","1922":"IIIB"},"71":{"1745":"07.01.2021","1662":"0","1233":"0","2069":"nein","1265":"3"},"88":{"1755":"07.01.2021","1963":"0","2116":"nein","1962":"IRS","2115":"nein","1960":"12","2026":"30,0","457":"8500/3","458":"2","317":"12100342","1959":"IRS","1923":"nein"},"74248":{"1753":"02.08.2021","2001":"D","2032":"Gr”áenregredientes Mammakarzinom links auf 12 Uhr. Lymphknoten unklarer Dignit„t beidseits axill„r.\nExulzeriertes Mammakarzinom rechts, laut Patientin weiter gr”áenregredient","1748":"02.08.2021","1614":"U"},"OS_EINWILLIGUNG":{"OS_EINWILLIGUNG_MELDEBEGRUENDUNG":"I"}}',
        null, 0, 2, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.2.1">
    <Absender Absender_ID="UKER_ONKOSTAR" Software_ID="ONKOSTAR" Installations_ID="2.9.8">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1015260170">
                <KrankenversichertenNr>T411337835</KrankenversichertenNr>
                <KrankenkassenNr>108310400</KrankenkassenNr>
                <Patienten_Nachname>Vader</Patienten_Nachname>
                <Patienten_Titel/>
                <Patienten_Namenszusatz/>
                <Patienten_Vornamen>Darth</Patienten_Vornamen>
                <Patienten_Geburtsname/>
                <Patienten_Geschlecht>W</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>06.06.1966</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Teststr. 132</Patienten_Strasse>
                        <Patienten_Hausnummer/>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>96111</Patienten_PLZ>
                        <Patienten_Ort>Testort</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
            <Menge_Meldung>
                <Meldung Meldung_ID="FK0000002" Melder_ID="FK">
                    <Meldedatum>16.12.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>diagnose</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="2">
                        <Primaertumor_ICD_Code>C50.9</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>07.01.2021</Diagnosedatum>
                        <Seitenlokalisation>R</Seitenlokalisation>
                    </Tumorzuordnung>
                    <Diagnose Tumor_ID="2">
                        <Primaertumor_ICD_Code>C50.9</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Primaertumor_Topographie_ICD_O>C50.9</Primaertumor_Topographie_ICD_O>
                        <Primaertumor_Topographie_ICD_O_Version>32</Primaertumor_Topographie_ICD_O_Version>
                        <Diagnosedatum>07.01.2021</Diagnosedatum>
                        <Diagnosesicherung>7</Diagnosesicherung>
                        <Seitenlokalisation>R</Seitenlokalisation>') ||
                    to_clob('<Menge_Histologie>
                            <Histologie Histologie_ID="88">
                                <Tumor_Histologiedatum>07.01.2021</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>12101342</Histologie_EinsendeNr>
                                <Morphologie_Code>8500/3</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Grading>2</Grading>
                            </Histologie>
                        </Menge_Histologie>
                        <cTNM TNM_ID="59">
                            <TNM_Datum>12.01.2021</TNM_Datum>
                            <TNM_Version>8</TNM_Version>
                            <TNM_c_p_u_Praefix_T>c</TNM_c_p_u_Praefix_T>
                            <TNM_T>4c</TNM_T>
                            <TNM_c_p_u_Praefix_N>c</TNM_c_p_u_Praefix_N>
                            <TNM_N>1</TNM_N>
                            <TNM_c_p_u_Praefix_M>c</TNM_c_p_u_Praefix_M>
                            <TNM_M>0</TNM_M>
                        </cTNM>
                        <Menge_Weitere_Klassifikation>
                            <Weitere_Klassifikation>
                                <Datum>12.01.2021</Datum>
                                <Name>UICC</Name>
                                <Stadium>IIIB</Stadium>
                            </Weitere_Klassifikation>
                        </Menge_Weitere_Klassifikation>
                        <Modul_Mamma>
                            <Praetherapeutischer_Menopausenstatus>3</Praetherapeutischer_Menopausenstatus>
                            <HormonrezeptorStatus_Oestrogen>P</HormonrezeptorStatus_Oestrogen>
                            <HormonrezeptorStatus_Progesteron>N</HormonrezeptorStatus_Progesteron>
                            <Her2neuStatus>N</Her2neuStatus>
                        </Modul_Mamma>
                        <Allgemeiner_Leistungszustand>U</Allgemeiner_Leistungszustand>
                    </Diagnose>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="FK">
            <Meldende_Stelle>FK</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>
'), null, null);

INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG (ID, ERKRANKUNG_ID, ART, ZU_MELDEN, LKR_STATUS, HINWEIS, MELDER_OE,
                                                  PROZEDURDATUM, TAN, DURCHFUEHRENDE_EINRICHTUNG,
                                                  DURCHFUEHRENDE_FACHABTEILUNG, RELEVANT_FORM_VALUES, XML_VALIDATION,
                                                  EXTERN, HAUPT_LKR_MELDUNGSTEIL, XML_DATEN, PATIENT_ID, MELDER_ID)
VALUES (3, 3, 1, 0, '0', null, 1, TO_TIMESTAMP('2021-01-14 00:00:00.000000', 'YYYY-MM-DD HH24:MI:SS.FF6'), 'FK0000003',
        null, null, '{"9":{"15087":"1","231":"C54.1","233":"","1226":"0","234":"7","1696":"14.01.2021","228":"C54.1","2030":"8441/3","2060":"E"},"2483":{"1090":"0","1086":"1","1097":"33","1096":"0","1082":"pT1 pN0 pM0","1755":"18.02.2021","2116":"nein","322":"0","2115":"nein","323":"0","457":"8441/3","1783":"R0","458":"3","1089":"0","317":"12104281","1923":"nein"},"2484":{"1755":"14.01.2021","2116":"nein","2115":"nein","457":"8441/3","458":"U","1923":"nein"},"3148":{"2070":"c","2087":"","2072":"c","2071":"c","2119":"nein","2118":"nein","2657":"I","2007":"14.01.2021","2117":"nein","1897":"0","1434":"cT1 cN0 cM0","1894":"0","1906":"nein","8327":"nein","1904":"1"},"61928":{"2070":"c","1440":"1","2087":"","2072":"c","2071":"c","1910":"pT1 pN0 cM0","2119":"nein","2118":"nein","2657":"IA","2007":"17.02.2021","2117":"nein","1906":"nein","8327":"nein","1915":"0","1914":"c","1913":"0","1439":"p","1911":"p","2527":"R0"},"OS_EINWILLIGUNG":{"OS_EINWILLIGUNG_MELDEBEGRUENDUNG":"I"}}',
        null, 1, 3, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.2.1">
    <Absender Absender_ID="UKER_ONKOSTAR" Software_ID="ONKOSTAR" Installations_ID="2.9.8">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1005596622">
                <KrankenversichertenNr>V783187819</KrankenversichertenNr>
                <KrankenkassenNr>103501080</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>Jane</Patienten_Vornamen>
                <Patienten_Geburtsname>Doe</Patienten_Geburtsname>
                <Patienten_Geschlecht>W</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>15.10.1964</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Testerstr. 123</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>90449</Patienten_PLZ>
                        <Patienten_Ort>Nrnberg</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
            <Menge_Meldung>
                <Meldung Meldung_ID="FK0000003" Melder_ID="999999">
                    <Meldedatum>23.11.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>diagnose</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>C54.1</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>14.01.2021</Diagnosedatum>
                        <Seitenlokalisation>T</Seitenlokalisation>
                    </Tumorzuordnung>
                    <Diagnose Tumor_ID="1">
                        <Primaertumor_ICD_Code>C54.1</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Primaertumor_Topographie_ICD_O>C54.1</Primaertumor_Topographie_ICD_O>
                        <Primaertumor_Topographie_ICD_O_Version>32</Primaertumor_Topographie_ICD_O_Version>
                        <Diagnosedatum>14.01.2021</Diagnosedatum>
                        <Diagnosesicherung>7</Diagnosesicherung>
                        <Seitenlokalisation>T</Seitenlokalisation>
                        <Menge_Histologie>
                            <Histologie Histologie_ID="2484">
                                <Tumor_Histologiedatum>14.01.2021</Tumor_Histologiedatum>
                                <Morphologie_Code>8441/3</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Grading>U</Grading>
                            </Histologie>') ||
            to_clob(' <Histologie Histologie_ID="2483">
                                <Tumor_Histologiedatum>18.02.2021</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>12104288</Histologie_EinsendeNr>
                                <Morphologie_Code>8441/3</Morphologie_Code>
                                <Morphologie_ICD_O_Version>33</Morphologie_ICD_O_Version>
                                <Grading>3</Grading>
                                <LK_untersucht>33</LK_untersucht>
                                <LK_befallen>0</LK_befallen>
                            </Histologie>
                        </Menge_Histologie>
                        <cTNM TNM_ID="3148">
                            <TNM_Datum>14.01.2021</TNM_Datum>
                            <TNM_Version>8</TNM_Version>
                            <TNM_c_p_u_Praefix_T>c</TNM_c_p_u_Praefix_T>
                            <TNM_T>1</TNM_T>
                            <TNM_c_p_u_Praefix_N>c</TNM_c_p_u_Praefix_N>
                            <TNM_N>0</TNM_N>
                            <TNM_c_p_u_Praefix_M>c</TNM_c_p_u_Praefix_M>
                            <TNM_M>0</TNM_M>
                        </cTNM>
                        <pTNM TNM_ID="2483">
                            <TNM_Datum>18.02.2021</TNM_Datum>
                            <TNM_Version>8</TNM_Version>
                            <TNM_c_p_u_Praefix_T>p</TNM_c_p_u_Praefix_T>
                            <TNM_T>1</TNM_T>
                            <TNM_c_p_u_Praefix_N>p</TNM_c_p_u_Praefix_N>
                            <TNM_N>0</TNM_N>
                            <TNM_c_p_u_Praefix_M>p</TNM_c_p_u_Praefix_M>
                            <TNM_M>0</TNM_M>
                            <TNM_L>L0</TNM_L>
                            <TNM_V>V0</TNM_V>
                        </pTNM>
                        <Menge_Weitere_Klassifikation>
                            <Weitere_Klassifikation>
                                <Datum>17.02.2021</Datum>
                                <Name>FIGO</Name>
                                <Stadium>IA</Stadium>
                            </Weitere_Klassifikation>
                            <Weitere_Klassifikation>
                                <Datum>14.01.2021</Datum>
                                <Name>FIGO</Name>
                                <Stadium>I</Stadium>
                            </Weitere_Klassifikation>
                        </Menge_Weitere_Klassifikation>
                    </Diagnose>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="999999">
            <Meldende_Stelle>999999</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>
'), null, null);
