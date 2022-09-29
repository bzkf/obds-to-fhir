ALTER SESSION SET CONTAINER = COGN12;
create table DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT
(
    ID              NUMBER(10),
    LKR_MELDUNG     NUMBER(10) not null,
    LKR_EXPORT      NUMBER(10) not null,
    TYP             NUMBER(10) not null,
    XML_DATEN       CLOB,
    VERSIONSNUMMER  NUMBER(10),
    REFERENZ_NUMMER VARCHAR2(50 char)
)
    /


INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (1, 80, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.1.2">
    <Absender Absender_ID="UKER" Software_ID="ONKOSTAR" Installations_ID="2.9.2">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1087655521">
                <KrankenversichertenNr>U295811460</KrankenversichertenNr>
                <KrankenkassenNr>107202793</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>17.12.1944</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Teststrasse 123</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91234</Patienten_PLZ>
                        <Patienten_Ort>Testort</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
            <Menge_Meldung>
                <Meldung Meldung_ID="UKER0000080" Melder_ID="UKER">
                    <Meldedatum>27.03.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>diagnose</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="3">') ||
            to_clob('<Primaertumor_ICD_Code>C61</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>15.07.2001</Diagnosedatum>
                        <Seitenlokalisation>T</Seitenlokalisation>
                    </Tumorzuordnung>
                    <Diagnose Tumor_ID="3">
                        <Primaertumor_ICD_Code>C61</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Primaertumor_Topographie_ICD_O>C61.9</Primaertumor_Topographie_ICD_O>
                        <Primaertumor_Topographie_ICD_O_Version>32</Primaertumor_Topographie_ICD_O_Version>
                        <Diagnosedatum>15.07.2000</Diagnosedatum>
                        <Diagnosesicherung>9</Diagnosesicherung>
                        <Seitenlokalisation>T</Seitenlokalisation>
                        <Menge_Histologie>
                            <Histologie>
                                <Morphologie_Code>8010/3</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                            </Histologie>
                        </Menge_Histologie>
                    </Diagnose>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="UKER">
            <Meldende_Stelle>UKER</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>
'), 1, '1087655521');


INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (2, 22, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.1.2">
    <Absender Absender_ID="UKER" Software_ID="ONKOSTAR" Installations_ID="2.9.2">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1077554421">
                <KrankenversichertenNr>80995999A16</KrankenversichertenNr>
                <KrankenkassenNr>168140186</KrankenkassenNr>
                <Patienten_Nachname>Wan</Patienten_Nachname>
                <Patienten_Titel/>
                <Patienten_Namenszusatz/>
                <Patienten_Vornamen>Obi</Patienten_Vornamen>
                <Patienten_Geburtsname/>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>03.03.1965</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Teststr. 122a</Patienten_Strasse>
                        <Patienten_Hausnummer/>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91234</Patienten_PLZ>
                        <Patienten_Ort>Testort</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>') ||
    to_clob(' <Menge_Meldung>
                <Meldung Meldung_ID="NC0000022" Melder_ID="NC">
                    <Meldedatum>27.04.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>diagnose</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>C71.5</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>04.01.2021</Diagnosedatum>
                        <Seitenlokalisation>T</Seitenlokalisation>
                    </Tumorzuordnung>
                    <Diagnose Tumor_ID="1">
                        <Primaertumor_ICD_Code>C71.5</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Primaertumor_Topographie_ICD_O>C71.51</Primaertumor_Topographie_ICD_O>
                        <Primaertumor_Topographie_ICD_O_Version>32</Primaertumor_Topographie_ICD_O_Version>
                        <Diagnosedatum>04.01.2021</Diagnosedatum>
                        <Diagnosesicherung>7</Diagnosesicherung>
                        <Seitenlokalisation>T</Seitenlokalisation>
                        <Menge_Histologie>
                            <Histologie Histologie_ID="304">
                                <Tumor_Histologiedatum>04.01.2021</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>N 4/21</Histologie_EinsendeNr>
                                <Morphologie_Code>9391/3</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Morphologie_Freitext>Ependymom</Morphologie_Freitext>
                                <Grading>T</Grading>
                            </Histologie>
                        </Menge_Histologie>
                        <Menge_Weitere_Klassifikation>
                            <Weitere_Klassifikation>
                                <Datum>04.01.2021</Datum>
                                <Name>WHO-2016</Name>
                                <Stadium>II</Stadium>
                            </Weitere_Klassifikation>
                        </Menge_Weitere_Klassifikation>
                    </Diagnose>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="NC">
            <Meldende_Stelle>NC</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>
'), 1, '1077554421');

INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (3, 23, 1, -1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.1.2">
    <Absender Absender_ID="UKER" Software_ID="ONKOSTAR" Installations_ID="2.9.2">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1077554421">
                <KrankenversichertenNr>80995999A16</KrankenversichertenNr>
                <KrankenkassenNr>168140186</KrankenkassenNr>
                <Patienten_Nachname>Wan</Patienten_Nachname>
                <Patienten_Titel/>
                <Patienten_Namenszusatz/>
                <Patienten_Vornamen>Obi</Patienten_Vornamen>
                <Patienten_Geburtsname/>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>03.03.1965</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Teststr. 122a</Patienten_Strasse>
                        <Patienten_Hausnummer/>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91234</Patienten_PLZ>
                        <Patienten_Ort>Testort</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
            <Menge_Meldung>
                <Meldung Meldung_ID="NC0000023" Melder_ID="NC">
                    <Meldedatum>27.04.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>behandlungsende</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>C71.5</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>04.01.2021</Diagnosedatum>
                        <Seitenlokalisation>T</Seitenlokalisation>
                    </Tumorzuordnung>') ||
          to_clob(' <Menge_OP>
                        <OP OP_ID="316">
                            <OP_Intention>K</OP_Intention>
                            <OP_Datum>07.01.2021</OP_Datum>
                            <Menge_OPS>
                                <OP_OPS>5-015.0</OP_OPS>
                                <OP_OPS>5-852.g0</OP_OPS>
                                <OP_OPS>5-930.00</OP_OPS>
                                <OP_OPS>5-984</OP_OPS>
                                <OP_OPS>5-010.14</OP_OPS>
                            </Menge_OPS>
                            <OP_OPS_Version>2021</OP_OPS_Version>
                            <Histologie Histologie_ID="304">
                                <Tumor_Histologiedatum>07.01.2021</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>N 4/21</Histologie_EinsendeNr>
                                <Morphologie_Code>9391/3</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Morphologie_Freitext>Ependymom</Morphologie_Freitext>
                                <Grading>T</Grading>
                            </Histologie>
                            <Residualstatus>
                                <Lokale_Beurteilung_Residualstatus>R2</Lokale_Beurteilung_Residualstatus>
                                <Gesamtbeurteilung_Residualstatus>R2</Gesamtbeurteilung_Residualstatus>
                            </Residualstatus>
                            <Menge_Komplikation>
                                <OP_Komplikation>U</OP_Komplikation>
                            </Menge_Komplikation>
                            <Menge_Operateur>
                                <Name_Operateur>Mustermann, Max</Name_Operateur>
                            </Menge_Operateur>
                        </OP>
                    </Menge_OP>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="NC">
            <Meldende_Stelle>NC</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>
'), null, '1077554421');


INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (4, 22, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.1.2">
    <Absender Absender_ID="UKER" Software_ID="ONKOSTAR" Installations_ID="2.9.2">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1055555550">
                <KrankenversichertenNr>Q00000000</KrankenversichertenNr>
                <KrankenkassenNr>10000000</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>11.09.1900</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Johnstr. 7</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91000</Patienten_PLZ>
                        <Patienten_Ort>Johncity</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
            <Menge_Meldung>
                <Meldung Meldung_ID="NC0000061" Melder_ID="NC">
                    <Meldedatum>06.05.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>behandlungsende</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>D42.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>04.01.2021</Diagnosedatum>
                        <Seitenlokalisation>T</Seitenlokalisation>
                    </Tumorzuordnung>') ||
          to_clob('<Menge_OP>
                        <OP OP_ID="741">
                            <OP_Intention>K</OP_Intention>
                            <OP_Datum>04.01.2021</OP_Datum>
                            <Menge_OPS>
                                <OP_OPS>5-015.4</OP_OPS>
                                <OP_OPS>5-852.g0</OP_OPS>
                                <OP_OPS>5-930.00</OP_OPS>
                                <OP_OPS>5-930.4</OP_OPS>
                                <OP_OPS>5-021.0</OP_OPS>
                                <OP_OPS>5-984</OP_OPS>
                                <OP_OPS>5-010.01</OP_OPS>
                                <OP_OPS>5-988.0</OP_OPS>
                            </Menge_OPS>
                            <OP_OPS_Version>2021</OP_OPS_Version>
                            <Histologie Histologie_ID="734">
                                <Tumor_Histologiedatum>04.01.2021</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>N 3/21</Histologie_EinsendeNr>
                                <Morphologie_Code>9539/1</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Grading>T</Grading>
                            </Histologie>
                            <Residualstatus>
                                <Lokale_Beurteilung_Residualstatus>R0</Lokale_Beurteilung_Residualstatus>
                                <Gesamtbeurteilung_Residualstatus>R0</Gesamtbeurteilung_Residualstatus>
                            </Residualstatus>
                            <Menge_Komplikation>
                                <OP_Komplikation>N</OP_Komplikation>
                            </Menge_Komplikation>
                            <Menge_Operateur>
                                <Name_Operateur>Darwin, Charles</Name_Operateur>
                                                                                                                      </Menge_Operateur>
                                                                                                                      </OP>
                                                                                                                      </Menge_OP>
                                                                                                                      </Meldung>
                                                                                                                      </Menge_Meldung>
                                                                                                                      </Patient>
                                                                                                                      </Menge_Patient>
                                                                                                                      <Menge_Melder>
            <Melder Melder_ID="NC">
            <Meldende_Stelle>NC</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>'), 1, '1055555550');






INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (5, 22, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.1.2">
    <Absender Absender_ID="UKER_ONKOSTAR" Software_ID="ONKOSTAR" Installations_ID="2.9.2">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1055555550">
                <KrankenversichertenNr>Q00000000</KrankenversichertenNr>
                <KrankenkassenNr>10000000</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>11.09.1900</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Johnstr 7</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91000</Patienten_PLZ>
                        <Patienten_Ort>Johncity</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
            <Menge_Meldung>
                <Meldung Meldung_ID="NC0000061" Melder_ID="NC">
                    <Meldedatum>10.05.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>behandlungsende</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>D42.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>04.01.2021</Diagnosedatum>
                        <Seitenlokalisation>T</Seitenlokalisation>
                    </Tumorzuordnung>') ||
          to_clob('<Menge_OP>
                        <OP OP_ID="741">
                            <OP_Intention>K</OP_Intention>
                            <OP_Datum>04.01.2021</OP_Datum>
                            <Menge_OPS>
                                <OP_OPS>5-015.4</OP_OPS>
                                <OP_OPS>5-852.g0</OP_OPS>
                                <OP_OPS>5-930.00</OP_OPS>
                                <OP_OPS>5-930.4</OP_OPS>
                                <OP_OPS>5-021.0</OP_OPS>
                                <OP_OPS>5-984</OP_OPS>
                                <OP_OPS>5-010.01</OP_OPS>
                                <OP_OPS>5-988.0</OP_OPS>
                            </Menge_OPS>
                            <OP_OPS_Version>2021</OP_OPS_Version>
                            <Histologie Histologie_ID="734">
                                <Tumor_Histologiedatum>04.01.2021</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>N 3/21</Histologie_EinsendeNr>
                                <Morphologie_Code>9539/1</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Morphologie_Freitext>Atypisches Meningeom</Morphologie_Freitext>
                                <Grading>T</Grading>
                            </Histologie>
                            <Residualstatus>
                                <Lokale_Beurteilung_Residualstatus>R0</Lokale_Beurteilung_Residualstatus>
                                <Gesamtbeurteilung_Residualstatus>R0</Gesamtbeurteilung_Residualstatus>
                            </Residualstatus>
                            <Menge_Komplikation>
                                <OP_Komplikation>N</OP_Komplikation>
                            </Menge_Komplikation>
                            <Menge_Operateur>
                                <Name_Operateur>Darwin, Charles</Name_Operateur>
                            </Menge_Operateur>
                        </OP>
                    </Menge_OP>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="NC">
            <Meldende_Stelle>NC</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>'), 1, '1055555550');





INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (6, 22, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.2.1">
    <Absender Absender_ID="UKER_ONKOSTAR" Software_ID="ONKOSTAR" Installations_ID="2.9.6">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1055555550">
                <KrankenversichertenNr>Q00000000</KrankenversichertenNr>
                <KrankenkassenNr>10000000</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>11.09.1900</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Johnstr 7</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91000</Patienten_PLZ>
                        <Patienten_Ort>Johncity</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
            <Menge_Meldung>
                <Meldung Meldung_ID="NC0000061" Melder_ID="NC">
                    <Meldedatum>20.05.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>behandlungsende</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>D42.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>04.01.2021</Diagnosedatum>
                        <Seitenlokalisation>T</Seitenlokalisation>
                    </Tumorzuordnung>') ||
          to_clob('<Menge_OP>
                        <OP OP_ID="741">
                            <OP_Intention>K</OP_Intention>
                            <OP_Datum>04.01.2021</OP_Datum>
                            <Menge_OPS>
                                <OP_OPS>5-015.4</OP_OPS>
                                <OP_OPS>5-852.g0</OP_OPS>
                                <OP_OPS>5-930.00</OP_OPS>
                                <OP_OPS>5-930.4</OP_OPS>
                                <OP_OPS>5-021.0</OP_OPS>
                                <OP_OPS>5-984</OP_OPS>
                                <OP_OPS>5-010.01</OP_OPS>
                                <OP_OPS>5-988.0</OP_OPS>
                            </Menge_OPS>
                            <OP_OPS_Version>2021</OP_OPS_Version>
                            <Histologie Histologie_ID="734">
                                <Tumor_Histologiedatum>04.01.2021</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>N 3/21</Histologie_EinsendeNr>
                                <Morphologie_Code>9539/1</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Grading>T</Grading>
                            </Histologie>
                            <Residualstatus>
                                <Lokale_Beurteilung_Residualstatus>R0</Lokale_Beurteilung_Residualstatus>
                                <Gesamtbeurteilung_Residualstatus>R0</Gesamtbeurteilung_Residualstatus>
                            </Residualstatus>
                            <Menge_Komplikation>
                                <OP_Komplikation>N</OP_Komplikation>
                            </Menge_Komplikation>
                            <Menge_Operateur>
                                <Name_Operateur>Darwin, Charles</Name_Operateur>
                            </Menge_Operateur>
                        </OP>
                    </Menge_OP>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="NC">
            <Meldende_Stelle>NC</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>'), 1, '1055555550');





INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (7, 22, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.1.2">
    <Absender Absender_ID="UKER" Software_ID="ONKOSTAR" Installations_ID="2.9.2">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1055555550">
                <KrankenversichertenNr>Q00000000</KrankenversichertenNr>
                <KrankenkassenNr>10000000</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>11.09.1900</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Johnstr 7</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91000</Patienten_PLZ>
                        <Patienten_Ort>Johncity</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>') ||
          to_clob('<Menge_Meldung>
                <Meldung Meldung_ID="NC0000060" Melder_ID="NC">
                    <Meldedatum>06.05.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>diagnose</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>D42.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>04.01.2021</Diagnosedatum>
                        <Seitenlokalisation>T</Seitenlokalisation>
                    </Tumorzuordnung>
                    <Diagnose Tumor_ID="1">
                        <Primaertumor_ICD_Code>D42.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Primaertumor_Topographie_ICD_O>C70.9</Primaertumor_Topographie_ICD_O>
                        <Primaertumor_Topographie_ICD_O_Version>32</Primaertumor_Topographie_ICD_O_Version>
                        <Diagnosedatum>04.01.2021</Diagnosedatum>
                        <Diagnosesicherung>7</Diagnosesicherung>
                        <Seitenlokalisation>T</Seitenlokalisation>
                        <Menge_Histologie>
                            <Histologie Histologie_ID="734">
                                <Tumor_Histologiedatum>04.01.2021</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>N 3/21</Histologie_EinsendeNr>
                                <Morphologie_Code>9539/1</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Grading>T</Grading>
                            </Histologie>
                        </Menge_Histologie>
                        <Menge_Weitere_Klassifikation>
                            <Weitere_Klassifikation>
                                <Datum>04.01.2021</Datum>
                                <Name>WHO-2016</Name>
                                <Stadium>II</Stadium>
                            </Weitere_Klassifikation>
                        </Menge_Weitere_Klassifikation>
                    </Diagnose>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="NC">
            <Meldende_Stelle>NC</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>'), 1, '1055555550');



INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (8, 22, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.1.2">
    <Absender Absender_ID="UKER_ONKOSTAR" Software_ID="ONKOSTAR" Installations_ID="2.9.2">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1055555550">
                <KrankenversichertenNr>Q00000000</KrankenversichertenNr>
                <KrankenkassenNr>10000000</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>11.09.1900</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Johnstr 7</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91000</Patienten_PLZ>
                        <Patienten_Ort>Johncity</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
            <Menge_Meldung>
                <Meldung Meldung_ID="NC0000060" Melder_ID="NC">
                    <Meldedatum>10.05.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>diagnose</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>D42.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>04.01.2021</Diagnosedatum>
                        <Seitenlokalisation>T</Seitenlokalisation>
                    </Tumorzuordnung>') ||
          to_clob('<Diagnose Tumor_ID="1">
                        <Primaertumor_ICD_Code>D42.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Primaertumor_Topographie_ICD_O>C70.9</Primaertumor_Topographie_ICD_O>
                        <Primaertumor_Topographie_ICD_O_Version>32</Primaertumor_Topographie_ICD_O_Version>
                        <Diagnosedatum>04.01.2021</Diagnosedatum>
                        <Diagnosesicherung>7</Diagnosesicherung>
                        <Seitenlokalisation>T</Seitenlokalisation>
                        <Menge_Histologie>
                            <Histologie Histologie_ID="734">
                                <Tumor_Histologiedatum>04.01.2021</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>N 3/21</Histologie_EinsendeNr>
                                <Morphologie_Code>9539/1</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Morphologie_Freitext>Atypisches Meningeom</Morphologie_Freitext>
                                <Grading>T</Grading>
                            </Histologie>
                        </Menge_Histologie>
                        <Menge_Weitere_Klassifikation>
                            <Weitere_Klassifikation>
                                <Datum>04.01.2021</Datum>
                                <Name>WHO-2016</Name>
                                <Stadium>II</Stadium>
                            </Weitere_Klassifikation>
                        </Menge_Weitere_Klassifikation>
                    </Diagnose>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="NC">
            <Meldende_Stelle>NC</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>'), 1, '1055555550');





INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (9, 22, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.2.1">
    <Absender Absender_ID="UKER_ONKOSTAR" Software_ID="ONKOSTAR" Installations_ID="2.9.6">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1055555550">
                <KrankenversichertenNr>Q00000000</KrankenversichertenNr>
                <KrankenkassenNr>10000000</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>11.09.1900</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Johnstr 7</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91000</Patienten_PLZ>
                        <Patienten_Ort>Johncity</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
            <Menge_Meldung>
                <Meldung Meldung_ID="NC0000060" Melder_ID="NC">
                    <Meldedatum>20.05.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>diagnose</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>D42.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>04.01.2021</Diagnosedatum>
                        <Seitenlokalisation>T</Seitenlokalisation>
                    </Tumorzuordnung>') ||
          to_clob('<Diagnose Tumor_ID="1">
                        <Primaertumor_ICD_Code>D42.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Primaertumor_Topographie_ICD_O>C70.9</Primaertumor_Topographie_ICD_O>
                        <Primaertumor_Topographie_ICD_O_Version>32</Primaertumor_Topographie_ICD_O_Version>
                        <Diagnosedatum>04.01.2021</Diagnosedatum>
                        <Diagnosesicherung>7</Diagnosesicherung>
                        <Seitenlokalisation>T</Seitenlokalisation>
                        <Menge_Histologie>
                            <Histologie Histologie_ID="734">
                                <Tumor_Histologiedatum>04.01.2021</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>N 3/21</Histologie_EinsendeNr>
                                <Morphologie_Code>9539/1</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Grading>T</Grading>
                            </Histologie>
                        </Menge_Histologie>
                        <Menge_Weitere_Klassifikation>
                            <Weitere_Klassifikation>
                                <Datum>04.01.2021</Datum>
                                <Name>WHO-2016</Name>
                                <Stadium>II</Stadium>
                            </Weitere_Klassifikation>
                        </Menge_Weitere_Klassifikation>
                    </Diagnose>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="NC">
            <Meldende_Stelle>NC</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>'), 1, '1055555550');



INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (10, 22, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.1.2">
    <Absender Absender_ID="UKER" Software_ID="ONKOSTAR" Installations_ID="2.9.2">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1055555550">
                <KrankenversichertenNr>Q00000000</KrankenversichertenNr>
                <KrankenkassenNr>10000000</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>11.09.1900</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Johnstr 7</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91000</Patienten_PLZ>
                        <Patienten_Ort>Johncity</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
            <Menge_Meldung>
                <Meldung Meldung_ID="NC0000666" Melder_ID="NC">
                    <Meldedatum>10.05.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>diagnose</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>D42.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>04.01.2021</Diagnosedatum>
                        <Seitenlokalisation>T</Seitenlokalisation>
                    </Tumorzuordnung>') ||
          to_clob('<Diagnose Tumor_ID="1">
                        <Primaertumor_ICD_Code>D42.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Primaertumor_Topographie_ICD_O>C70.9</Primaertumor_Topographie_ICD_O>
                        <Primaertumor_Topographie_ICD_O_Version>32</Primaertumor_Topographie_ICD_O_Version>
                        <Diagnosedatum>04.01.2021</Diagnosedatum>
                        <Diagnosesicherung>7</Diagnosesicherung>
                        <Seitenlokalisation>T</Seitenlokalisation>
                        <Menge_Histologie>
                            <Histologie Histologie_ID="739">
                                <Tumor_Histologiedatum>10.01.2021</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>N 3/21</Histologie_EinsendeNr>
                                <Morphologie_Code>8085/3</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Grading>T</Grading>
                            </Histologie>
                        </Menge_Histologie>
                        <Menge_Weitere_Klassifikation>
                            <Weitere_Klassifikation>
                                <Datum>04.01.2021</Datum>
                                <Name>WHO-2016</Name>
                                <Stadium>II</Stadium>
                            </Weitere_Klassifikation>
                        </Menge_Weitere_Klassifikation>
                    </Diagnose>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="NC">
            <Meldende_Stelle>NC</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>'), 1, '1055555550');





INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (11, 22, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.1.2">
    <Absender Absender_ID="UKER_ONKOSTAR" Software_ID="ONKOSTAR" Installations_ID="2.9.2">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1055555550">
                <KrankenversichertenNr>Q00000000</KrankenversichertenNr>
                <KrankenkassenNr>10000000</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>11.09.1900</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Johnstr 7</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91000</Patienten_PLZ>
                        <Patienten_Ort>Johncity</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
            <Menge_Meldung>
                <Meldung Meldung_ID="NC0000666" Melder_ID="NC">
                    <Meldedatum>06.05.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>diagnose</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>D42.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>04.01.2021</Diagnosedatum>
                        <Seitenlokalisation>T</Seitenlokalisation>
                    </Tumorzuordnung>') ||
          to_clob('<Diagnose Tumor_ID="1">
                        <Primaertumor_ICD_Code>D42.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Primaertumor_Topographie_ICD_O>C70.9</Primaertumor_Topographie_ICD_O>
                        <Primaertumor_Topographie_ICD_O_Version>32</Primaertumor_Topographie_ICD_O_Version>
                        <Diagnosedatum>04.01.2021</Diagnosedatum>
                        <Diagnosesicherung>7</Diagnosesicherung>
                        <Seitenlokalisation>T</Seitenlokalisation>
                        <Menge_Histologie>
                            <Histologie Histologie_ID="732">
                                <Tumor_Histologiedatum>04.01.2021</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>N 3/21</Histologie_EinsendeNr>
                                <Morphologie_Code>8077/2</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Morphologie_Freitext>Atypisches Meningeom</Morphologie_Freitext>
                                <Grading>T</Grading>
                            </Histologie>
                        </Menge_Histologie>
                        <Menge_Weitere_Klassifikation>
                            <Weitere_Klassifikation>
                                <Datum>04.01.2021</Datum>
                                <Name>WHO-2016</Name>
                                <Stadium>II</Stadium>
                            </Weitere_Klassifikation>
                        </Menge_Weitere_Klassifikation>
                    </Diagnose>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="NC">
            <Meldende_Stelle>NC</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>'), 1, '1055555550');
