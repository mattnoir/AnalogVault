package com.analogvault.util

import kotlin.math.log2
import kotlin.math.sqrt
import kotlin.math.pow

object Constants {
    val STORAGE_TYPES = listOf("Shelf","Fridge","Freezer","Cool Dark Place","Custom")
    val FILM_TYPES = listOf("Color Negative (C-41)","Black & White","Slide (E-6)","Infrared","Instant")
    val ISOS = listOf(25,50,64,100,160,200,400,800,1600,3200,6400)
    val SHUTTER_SPEEDS = listOf(
        "1/4000","1/2000","1/1000","1/500","1/250","1/125","1/60","1/30",
        "1/15","1/8","1/4","1/2","1s","2s","4s","8s","15s","30s","B"
    )
    val APERTURES = listOf(1.0,1.2,1.4,1.8,2.0,2.8,3.5,4.0,5.6,8.0,11.0,16.0,22.0,32.0)
    val METERING_TYPES = listOf("Evaluative/Matrix","Center-Weighted","Spot","Highlight-Weighted")
    val DEVELOP_PROCESSES = listOf("C-41 (Color)","B&W (Standard)","B&W (Stand)","B&W (Semi-Stand)","E-6 (Slide)","Custom")
    val SCAN_METHODS = listOf("Flatbed Scanner","DSLR/Mirrorless","Phone Scan","Lab Scan","Drum Scan")
    val ACCESSORY_TYPES = listOf("Filter","Flash","Tripod","Cable Release","Light Meter","Film Changing Bag","Darkroom Equipment","Bag/Case","Strap","Lens Hood","Other")
    val CHEM_TYPES = listOf("Developer","Fixer","Blix","Bleach","Stop Bath","Stabiliser","Wetting Agent","Custom")
    val CHEM_UNITS = listOf("ml","L","g","oz","mixed (L)")
    val CAMERA_FORMATS = listOf("35mm","120 (MF)","4x5","110","Instant")
    val CONDITIONS = listOf("Mint","Excellent","Good","Fair","Needs CLA")
    val LENS_CONDITIONS = listOf("Mint","Excellent","Good","Fair")

    val FILM_DB = listOf(
        // Kodak Color
        "Kodak Portra 160","Kodak Portra 400","Kodak Portra 800",
        "Kodak Ektar 100","Kodak Ektar 25",
        "Kodak ColorPlus 200","Kodak UltraMax 400","Kodak Gold 200","Kodak Gold 400",
        "Kodak Funsaver 800","Kodak Ektachrome E100",
        // Kodak B&W
        "Kodak T-Max 100","Kodak T-Max 400","Kodak T-Max P3200",
        "Kodak Tri-X 400","Kodak Tri-X 320","Kodak Double-X 250","Kodak Plus-X 125",
        "Kodak Panatomic-X 32","Kodak Technical Pan",
        // Fujifilm Color
        "Fujifilm Provia 100F","Fujifilm Velvia 50","Fujifilm Velvia 100",
        "Fujifilm Superia 100","Fujifilm Superia 200","Fujifilm Superia 400",
        "Fujifilm Superia X-TRA 400","Fujifilm Superia 800","Fujifilm C200",
        "Fujifilm Fujicolor 100","Fujifilm Fujicolor 200",
        // Fujifilm B&W
        "Fujifilm Acros 100 II","Fujifilm Neopan 400","Fujifilm Neopan 1600",
        // Ilford
        "Ilford HP5 Plus 400","Ilford FP4 Plus 125","Ilford SFX 200",
        "Ilford Delta 100","Ilford Delta 400","Ilford Delta 3200",
        "Ilford Pan F Plus 50","Ilford XP2 Super 400","Ilford Ortho Plus 80",
        "Ilford PAN 100","Ilford PAN 400","Ilford Mark V 400",
        // Lomography
        "Lomography Color Negative 100","Lomography Color Negative 400","Lomography Color Negative 800",
        "Lomography Lady Grey 400","Lomography Babylon Kino 13","Lomography Earl Grey 100",
        "Lomography Berlin Kino 400","Lomography Purple 400","Lomography Redscale XR 50-200",
        "Lomography LomoChrome Metropolis","Lomography LomoChrome Purple","Lomography LomoChrome Turquoise",
        // CineStill
        "CineStill 50D","CineStill 800T","CineStill 400D","CineStill BwXX",
        // Rollei
        "Rollei RPX 25","Rollei RPX 100","Rollei RPX 400",
        "Rollei Infrared 400","Rollei Ortho 25 Plus","Rollei Superpan 200","Rollei CR200",
        // Foma/Fomapan
        "Fomapan 100","Fomapan 200","Fomapan 400","Fomapan R 100",
        // Kentmere
        "Kentmere Pan 100","Kentmere Pan 400",
        // Bergger
        "Bergger Pancro 400","Bergger Prestige 400",
        // Adox
        "Adox CMS 20 II","Adox HR 50","Adox Scala 160","Adox Color Mission 200",
        // Ferrania
        "Ferrania P30 Alpha 80","Film Ferrania Solaris 200","Film Ferrania Solaris 400",
        // Orwo
        "Orwo N74 Plus 400","Orwo UN54 100","Orwo DP31",
        // Kosmo Foto
        "Kosmo Foto Mono 100","Kosmo Foto Agent Shadow 400",
        // Harman
        "Harman Phoenix 200","Harman Direct Positive 100",
        // Lucky / Shanghai / Chinese
        "Lucky SHD 100","Lucky SHD 400","Shanghai GP3 100",
        // Tasma / Soviet
        "Tasma Foto 64","Svema Foto 64","Svema Foto 200",
        // JCH StreetPan
        "JCH StreetPan 400",
        // Expired / vintage (popular)
        "AgfaPhoto Vista 200","AgfaPhoto Vista Plus 400","Agfa APX 100","Agfa APX 400",
        "Konica VX 400","Konica Centuria 200","Konica Centuria 400",
        // Instant
        "Polaroid 600","Polaroid i-Type","Polaroid SX-70","Impossible PX 70","Fujifilm Instax Mini","Fujifilm Instax Square","Fujifilm Instax Wide"
    )
    val CAMERA_DB = listOf(
        // Nikon
        "Nikon F","Nikon F2","Nikon F3","Nikon F4","Nikon F5","Nikon F6",
        "Nikon FM","Nikon FM2","Nikon FM2n","Nikon FM3A","Nikon FE","Nikon FE2",
        "Nikon FA","Nikon F80","Nikon F100","Nikon EM","Nikon FG","Nikon N90s",
        "Nikon L35AF","Nikon 35Ti","Nikon 28Ti","Nikon Coolpix 950",
        // Canon
        "Canon AE-1","Canon AE-1 Program","Canon A-1","Canon F-1","Canon New F-1",
        "Canon EOS-1V","Canon EOS-3","Canon EOS-1","Canon T70","Canon T90",
        "Canon AL-1","Canon QL17 GIII","Canon Canonet QL19","Canon Sure Shot AF35M",
        "Canon ELPH","Canon Demi","Canon Dial 35",
        // Olympus
        "Olympus OM-1","Olympus OM-1n","Olympus OM-2","Olympus OM-2n","Olympus OM-3",
        "Olympus OM-3Ti","Olympus OM-4","Olympus OM-4Ti","Olympus OM-10","Olympus OM-20",
        "Olympus XA","Olympus XA2","Olympus mju-II","Olympus Stylus Epic","Olympus Trip 35",
        "Olympus Pen F","Olympus Pen FT","Olympus Pen EE","Olympus LT-1",
        // Pentax
        "Pentax K1000","Pentax ME","Pentax ME Super","Pentax MX","Pentax LX",
        "Pentax 67","Pentax 67II","Pentax 645","Pentax 645N","Pentax Spotmatic",
        "Pentax Spotmatic F","Pentax Espio 120Mi","Pentax IQZoom 90WR",
        // Minolta
        "Minolta X-700","Minolta X-570","Minolta XG-M","Minolta SRT 101","Minolta SRT 102",
        "Minolta SRT 202","Minolta X-300","Minolta Dynax 9","Minolta Hi-Matic 7sII",
        "Minolta TC-1","Minolta CLE","Minolta CL",
        // Leica 35mm
        "Leica M6","Leica M6 TTL","Leica M7","Leica MP","Leica M-A","Leica M3","Leica M2",
        "Leica M4","Leica M4-P","Leica M5","Leica M6J","Leica IIIf","Leica IIIg",
        "Leica IIIa","Leica CL","Leica Minilux","Leica CM",
        // Voigtländer
        "Voigtländer Bessa R","Voigtländer Bessa R2","Voigtländer Bessa R2A","Voigtländer Bessa R3A",
        "Voigtländer Bessa L","Voigtländer Bessa T","Voigtländer Vitessa L","Voigtländer Prominent",
        // Contax
        "Contax T","Contax T2","Contax T3","Contax G1","Contax G2","Contax RTS III",
        "Contax S2","Contax Aria","Contax 645",
        // Zeiss / Rollei
        "Zeiss Ikon ZM","Rollei 35","Rollei 35 S","Rollei 35 SE","Rollei 35 T",
        "Rollei 35 Classic","Rollei 35 RF","Rollei 35 AF",
        // Medium format — Hasselblad
        "Hasselblad 500 C","Hasselblad 500 C/M","Hasselblad 503CW","Hasselblad 501CM",
        "Hasselblad 2000FCW","Hasselblad XPan","Hasselblad XPan II","Hasselblad Superwide C",
        // Medium format — Mamiya
        "Mamiya RB67 Pro-S","Mamiya RB67","Mamiya RZ67","Mamiya RZ67 Pro II",
        "Mamiya 7","Mamiya 7 II","Mamiya 6","Mamiya 645 Pro","Mamiya C330",
        // Medium format — Rollei/Rolleiflex
        "Rolleiflex 2.8F","Rolleiflex 2.8E","Rolleiflex 3.5F","Rolleiflex T",
        "Rolleicord V","Rolleicord Va","Rolleiflex SL66","Rolleiflex 6008",
        // Medium format — other
        "Yashica Mat-124G","Yashica Mat","Yashica 124","Yashica 635",
        "Bronica ETRSi","Bronica ETRS","Bronica SQAi","Bronica GS-1","Bronica SQ-A",
        "Fujifilm GF670","Fujifilm GF670W","Fujifilm GW690 III","Fujifilm GSW690 III",
        "Fujifilm GA645","Kowa Six","Kowa Super 66","Seagull 4B",
        "Pentacon Six","Exakta 66","Kiev 60","Kiev 88",
        // Large format
        "Linhof Technika 4x5","Linhof Master Technika","Chamonix 4x5","Shen-Hao 4x5",
        "Toyo 45A","Arca-Swiss F-Line","Wista 45","Horseman 45FA",
        "Graflex Speed Graphic","Graflex Crown Graphic","Toyo 810G",
        // Rangefinders / compact
        "Canonet QL17 GIII","Yashica Electro 35 GSN","Konica Auto S2","Minox GT-E",
        "Nikon 35Ti","Nikon 28Ti","Leica Minilux","Contax T3","Ricoh GR1v","Ricoh GR1s",
        "Fujifilm Klasse W","Fujifilm Natura S","Mju-II","Konica Big Mini","Nikon L35AF",
        // Toy / Lomography
        "Lomo LC-A","Lomo LC-A+","Lomo LC-Wide","Lomography Sprocket Rocket",
        "Holga 120N","Holga 120SF","Diana F+","Diana Mini",
        "Vivitar Ultra Wide & Slim","Superheadz Golden Half",
        // SLR others
        "Praktica MTL 5","Zenit E","Zenit 12","Exakta Varex IIa","Alpa 12 TC",
        "Konica Autoreflex T","Yashica FX-3 Super","Chinon CE-4s"
    )
    val LENS_DB = listOf(
        // Nikon AI/AIS
        "Nikkor 20mm f/3.5","Nikkor 24mm f/2.8","Nikkor 28mm f/2","Nikkor 28mm f/2.8",
        "Nikkor 35mm f/1.4","Nikkor 35mm f/2","Nikkor 35mm f/2.8",
        "Nikkor 50mm f/1.2","Nikkor 50mm f/1.4","Nikkor 50mm f/1.8","Nikkor 50mm f/2",
        "Nikkor 55mm f/2.8 Micro","Nikkor 85mm f/1.4","Nikkor 85mm f/1.8","Nikkor 85mm f/2",
        "Nikkor 105mm f/1.8","Nikkor 105mm f/2.5","Nikkor 135mm f/2","Nikkor 135mm f/2.8",
        "Nikkor 180mm f/2.8","Nikkor 200mm f/4","Nikkor 300mm f/4.5",
        // Canon FD
        "Canon FD 17mm f/4","Canon FD 24mm f/1.4 L","Canon FD 24mm f/2.8",
        "Canon FD 28mm f/2","Canon FD 28mm f/2.8","Canon FD 35mm f/2","Canon FD 35mm f/3.5",
        "Canon FD 50mm f/1.2 L","Canon FD 50mm f/1.4","Canon FD 50mm f/1.8","Canon FD 50mm f/3.5 Macro",
        "Canon FD 85mm f/1.2 L","Canon FD 85mm f/1.8","Canon FD 100mm f/2","Canon FD 135mm f/2",
        "Canon FD 200mm f/2.8","Canon FD 300mm f/4 L",
        // Olympus OM Zuiko
        "Olympus Zuiko 21mm f/3.5","Olympus Zuiko 24mm f/2","Olympus Zuiko 28mm f/2",
        "Olympus Zuiko 28mm f/2.8","Olympus Zuiko 35mm f/2","Olympus Zuiko 50mm f/1.2",
        "Olympus Zuiko 50mm f/1.4","Olympus Zuiko 50mm f/1.8","Olympus Zuiko 85mm f/2",
        "Olympus Zuiko 100mm f/2","Olympus Zuiko 135mm f/2.8","Olympus Zuiko 200mm f/4",
        // Pentax SMC
        "SMC Pentax-A 28mm f/2.8","SMC Pentax-A 50mm f/1.2","SMC Pentax-A 50mm f/1.4",
        "SMC Pentax-A 50mm f/1.7","SMC Pentax-A 85mm f/1.4","SMC Pentax-A 135mm f/2.8",
        "SMC Takumar 28mm f/3.5","SMC Takumar 35mm f/3.5","SMC Takumar 50mm f/1.4",
        "Super Takumar 50mm f/1.4","Super Takumar 55mm f/1.8","Super Takumar 85mm f/1.9",
        // Minolta MD/MC
        "Minolta MD 28mm f/2.8","Minolta MD 35mm f/1.8","Minolta MD 50mm f/1.4",
        "Minolta MD 50mm f/1.7","Minolta MD 85mm f/2","Minolta MD 100mm f/2.5",
        "Minolta MC 58mm f/1.4","Minolta MC Rokkor 55mm f/1.7","Minolta MC 135mm f/2.8",
        // Leica M
        "Leica Summilux-M 21mm f/1.4 ASPH","Leica Elmarit-M 21mm f/2.8 ASPH",
        "Leica Elmarit-M 28mm f/2.8 ASPH","Leica Summicron-M 28mm f/2 ASPH",
        "Leica Summilux-M 35mm f/1.4","Leica Summicron-M 35mm f/2",
        "Leica Summilux-M 50mm f/1.4","Leica Summilux-M 50mm f/1.4 ASPH",
        "Leica Summicron-M 50mm f/2","Leica Elmar-M 50mm f/2.8",
        "Leica Noctilux-M 50mm f/0.95","Leica Noctilux-M 50mm f/1",
        "Leica APO-Summicron-M 75mm f/2","Leica Summilux-M 75mm f/1.4",
        "Leica APO-Summicron-M 90mm f/2","Leica Elmarit-M 90mm f/2.8",
        "Leica Summicron-M 90mm f/2","Leica APO-Telyt-M 135mm f/3.4",
        // Voigtländer VM
        "Voigtländer Color-Skopar 21mm f/4","Voigtländer Ultron 21mm f/1.8",
        "Voigtländer Color-Skopar 28mm f/3.5","Voigtländer Ultron 28mm f/2",
        "Voigtländer Nokton 35mm f/1.2","Voigtländer Nokton 35mm f/1.4","Voigtländer Skopar 35mm f/2.5",
        "Voigtländer Nokton 50mm f/1","Voigtländer Nokton 50mm f/1.5","Voigtländer Heliar 50mm f/3.5",
        "Voigtländer APO-Lanthar 50mm f/2","Voigtländer Nokton 75mm f/1.5",
        "Voigtländer APO-Lanthar 90mm f/3.5",
        // Zeiss ZM
        "Zeiss C Biogon 21mm f/4.5","Zeiss Biogon 25mm f/2.8","Zeiss Biogon 28mm f/2.8",
        "Zeiss C Sonnar 50mm f/1.5","Zeiss Planar 50mm f/2","Zeiss Sonnar 85mm f/2",
        "Zeiss Tele-Tessar 85mm f/4",
        // Soviet / Russian
        "Helios 44-2 58mm f/2","Helios 44M 58mm f/2","Helios 103 53mm f/1.8",
        "Jupiter-3 50mm f/1.5","Jupiter-8 50mm f/2","Jupiter-9 85mm f/2","Jupiter-11 135mm f/4",
        "Industar-50-2 50mm f/3.5","Industar-26M 50mm f/2.8","Industar-61 52mm f/2.8",
        "MIR-1B 37mm f/2.8","MIR-20M 20mm f/3.5","LOMO Minitar-1 32mm f/2.8",
        // M42 others
        "Carl Zeiss Jena Flektogon 35mm f/2.4","Carl Zeiss Jena Biotar 58mm f/2",
        "Carl Zeiss Jena Pancolar 50mm f/1.8","Carl Zeiss Jena Tessar 50mm f/2.8",
        "Fujinon 55mm f/1.8","Mamiya/Sekor 50mm f/2",
        // C/Y (Contax/Yashica)
        "Zeiss Distagon 21mm f/2.8","Zeiss Distagon 28mm f/2","Zeiss Distagon 35mm f/1.4",
        "Zeiss Distagon 35mm f/2","Zeiss Planar 50mm f/1.4","Zeiss Planar 85mm f/1.4",
        "Zeiss Sonnar 85mm f/2.8","Zeiss Sonnar 135mm f/2.8","Zeiss Tele-Tessar 200mm f/3.5",
        // Medium format — Hasselblad V
        "Zeiss Distagon 40mm f/4 CF","Zeiss Distagon 50mm f/4 CF","Zeiss Distagon 60mm f/3.5 CF",
        "Zeiss Planar 80mm f/2.8 CF","Zeiss Planar 100mm f/3.5","Zeiss Planar 110mm f/2 FE",
        "Zeiss Sonnar 150mm f/4 CF","Zeiss Sonnar 180mm f/4 CF","Zeiss Tele-Tessar 350mm f/5.6",
        // Medium format — Mamiya RB/RZ
        "Mamiya Sekor C 50mm f/4.5","Mamiya Sekor C 65mm f/4.5","Mamiya Sekor C 90mm f/3.5",
        "Mamiya Sekor C 127mm f/3.8","Mamiya Sekor C 180mm f/4.5",
        // Specialty
        "Lensbaby Composer Pro","Lomography Petzval 85mm f/2.2","Lomography Daguerreotype Achromat 2.9/64"
    )
    val DEV_DB = listOf(
        "Kodak D-76","Kodak HC-110","Ilford ID-11","Ilford Ilfosol 3","Ilford Microphen",
        "Rodinal (R09)","Caffenol-C","Xtol","Pyrocat HD","PMK Pyro","Cinestill Df96 Monobath",
        "Kodak C-41 Kit","Tetenal Colortec C-41","Unicolor C-41","Cinestill CS41"
    )
    val FIX_DB = listOf(
        "Ilford Rapid Fixer","Kodak Rapid Fixer","Sprint Record Fixer","TF-4 Archival Fixer","Sodium Thiosulfate"
    )

    // Mount groups
    data class MountGroup(val native: List<String>, val adapters: List<String>)
    val MOUNT_GROUPS: Map<String, MountGroup> = mapOf(
        "Nikon F"         to MountGroup(listOf("Nikon F","Nikon AI","Nikon AIS","Nikon Non-AI"), listOf("M42","Canon FD","Contax/Yashica","Olympus OM","Minolta MD","Leica R","Pentax K","T2")),
        "Canon FD"        to MountGroup(listOf("Canon FD","Canon FL"),                            listOf("M42","T2")),
        "Canon EF"        to MountGroup(listOf("Canon EF","Canon EF-S"),                          listOf("M42","Nikon F","Leica R","Olympus OM","Contax/Yashica","Minolta MD","Pentax K","T2")),
        "M42"             to MountGroup(listOf("M42"),                                             listOf("T2")),
        "Olympus OM"      to MountGroup(listOf("Olympus OM"),                                     listOf("M42","T2")),
        "Minolta MD"      to MountGroup(listOf("Minolta MD","Minolta MC","Minolta SR"),            listOf("M42","T2")),
        "Pentax K"        to MountGroup(listOf("Pentax K","Pentax KA","Pentax KAF","Pentax M42"), listOf("M42","T2")),
        "Contax/Yashica"  to MountGroup(listOf("Contax/Yashica","C/Y"),                           listOf("M42","T2")),
        "Leica M"         to MountGroup(listOf("Leica M","Voigtländer VM","Zeiss ZM"),            listOf("Leica L39/LTM","T2")),
        "Leica R"         to MountGroup(listOf("Leica R"),                                         listOf("M42","T2")),
        "Leica L39/LTM"   to MountGroup(listOf("Leica L39/LTM","LTM","L39"),                     listOf("T2")),
        "Hasselblad V"    to MountGroup(listOf("Hasselblad V"),                                    listOf("T2")),
        "Mamiya 645"      to MountGroup(listOf("Mamiya 645"),                                      listOf("T2")),
        "Mamiya RB/RZ"    to MountGroup(listOf("Mamiya RB67","Mamiya RZ67"),                      listOf("T2")),
        "Bronica ETR"     to MountGroup(listOf("Bronica ETR"),                                     listOf("T2")),
        "T2"              to MountGroup(listOf("T2"),                                               emptyList()),
    )
    val COMMON_MOUNTS = MOUNT_GROUPS.keys.toList()

    fun mountCompat(cameraMount: String, lensMount: String, manualOverrides: List<String> = emptyList()): String {
        if (cameraMount.isBlank() || lensMount.isBlank()) return "unknown"
        if (cameraMount == lensMount) return "native"
        if (manualOverrides.contains(lensMount)) return "adapter"
        val group = MOUNT_GROUPS[cameraMount]
            ?: MOUNT_GROUPS.values.firstOrNull { it.native.contains(cameraMount) }
            ?: return "unknown"
        if (group.native.contains(lensMount)) return "native"
        if (group.adapters.contains(lensMount)) return "adapter"
        return "incompatible"
    }

    // EV helpers
    fun evalShutter(s: String): Double {
        if (s.isBlank()) return 1.0/125
        if (s.endsWith("s")) return s.dropLast(1).toDoubleOrNull() ?: 1.0
        if (s == "B") return 30.0
        val parts = s.split("/")
        return if (parts.size == 2) parts[0].toDouble() / parts[1].toDouble() else s.toDoubleOrNull() ?: 1.0/125
    }
    fun calcEV(iso: Int, shutter: String, aperture: Double): Double {
        val t = evalShutter(shutter)
        return log2(aperture * aperture / t) - log2(iso / 100.0)
    }
    fun calcAperture(iso: Int, shutter: String, targetEV: Double): Double {
        val t = evalShutter(shutter)
        val a2 = t * 2.0.pow(targetEV) * (iso / 100.0)
        return sqrt(maxOf(a2, 1.0))
    }
    // Film metadata: name -> Triple(brand, iso, type)
    val FILM_METADATA: Map<String, Triple<String, Int, String>> = mapOf(
        // Kodak Color
        "Kodak Portra 160"       to Triple("Kodak",160,"Color Negative (C-41)"),
        "Kodak Portra 400"       to Triple("Kodak",400,"Color Negative (C-41)"),
        "Kodak Portra 800"       to Triple("Kodak",800,"Color Negative (C-41)"),
        "Kodak Ektar 100"        to Triple("Kodak",100,"Color Negative (C-41)"),
        "Kodak Ektar 25"         to Triple("Kodak",25,"Color Negative (C-41)"),
        "Kodak ColorPlus 200"    to Triple("Kodak",200,"Color Negative (C-41)"),
        "Kodak UltraMax 400"     to Triple("Kodak",400,"Color Negative (C-41)"),
        "Kodak Gold 200"         to Triple("Kodak",200,"Color Negative (C-41)"),
        "Kodak Gold 400"         to Triple("Kodak",400,"Color Negative (C-41)"),
        "Kodak Funsaver 800"     to Triple("Kodak",800,"Color Negative (C-41)"),
        "Kodak Ektachrome E100"  to Triple("Kodak",100,"Slide (E-6)"),
        // Kodak B&W
        "Kodak T-Max 100"        to Triple("Kodak",100,"Black & White"),
        "Kodak T-Max 400"        to Triple("Kodak",400,"Black & White"),
        "Kodak T-Max P3200"      to Triple("Kodak",3200,"Black & White"),
        "Kodak Tri-X 400"        to Triple("Kodak",400,"Black & White"),
        "Kodak Tri-X 320"        to Triple("Kodak",320,"Black & White"),
        "Kodak Double-X 250"     to Triple("Kodak",250,"Black & White"),
        "Kodak Plus-X 125"       to Triple("Kodak",125,"Black & White"),
        "Kodak Panatomic-X 32"   to Triple("Kodak",32,"Black & White"),
        "Kodak Technical Pan"    to Triple("Kodak",25,"Black & White"),
        // Fujifilm Color
        "Fujifilm Provia 100F"   to Triple("Fujifilm",100,"Slide (E-6)"),
        "Fujifilm Velvia 50"     to Triple("Fujifilm",50,"Slide (E-6)"),
        "Fujifilm Velvia 100"    to Triple("Fujifilm",100,"Slide (E-6)"),
        "Fujifilm Superia 100"   to Triple("Fujifilm",100,"Color Negative (C-41)"),
        "Fujifilm Superia 200"   to Triple("Fujifilm",200,"Color Negative (C-41)"),
        "Fujifilm Superia 400"   to Triple("Fujifilm",400,"Color Negative (C-41)"),
        "Fujifilm Superia X-TRA 400" to Triple("Fujifilm",400,"Color Negative (C-41)"),
        "Fujifilm Superia 800"   to Triple("Fujifilm",800,"Color Negative (C-41)"),
        "Fujifilm C200"          to Triple("Fujifilm",200,"Color Negative (C-41)"),
        "Fujifilm Fujicolor 100" to Triple("Fujifilm",100,"Color Negative (C-41)"),
        "Fujifilm Fujicolor 200" to Triple("Fujifilm",200,"Color Negative (C-41)"),
        // Fujifilm B&W
        "Fujifilm Acros 100 II"  to Triple("Fujifilm",100,"Black & White"),
        "Fujifilm Neopan 400"    to Triple("Fujifilm",400,"Black & White"),
        "Fujifilm Neopan 1600"   to Triple("Fujifilm",1600,"Black & White"),
        // Ilford
        "Ilford HP5 Plus 400"    to Triple("Ilford",400,"Black & White"),
        "Ilford FP4 Plus 125"    to Triple("Ilford",125,"Black & White"),
        "Ilford SFX 200"         to Triple("Ilford",200,"Black & White"),
        "Ilford Delta 100"       to Triple("Ilford",100,"Black & White"),
        "Ilford Delta 400"       to Triple("Ilford",400,"Black & White"),
        "Ilford Delta 3200"      to Triple("Ilford",3200,"Black & White"),
        "Ilford Pan F Plus 50"   to Triple("Ilford",50,"Black & White"),
        "Ilford XP2 Super 400"   to Triple("Ilford",400,"Color Negative (C-41)"),
        "Ilford Ortho Plus 80"   to Triple("Ilford",80,"Black & White"),
        "Ilford PAN 100"         to Triple("Ilford",100,"Black & White"),
        "Ilford PAN 400"         to Triple("Ilford",400,"Black & White"),
        // Lomography
        "Lomography Color Negative 100" to Triple("Lomography",100,"Color Negative (C-41)"),
        "Lomography Color Negative 400" to Triple("Lomography",400,"Color Negative (C-41)"),
        "Lomography Color Negative 800" to Triple("Lomography",800,"Color Negative (C-41)"),
        "Lomography Lady Grey 400"  to Triple("Lomography",400,"Black & White"),
        "Lomography Babylon Kino 13" to Triple("Lomography",13,"Black & White"),
        "Lomography Earl Grey 100"  to Triple("Lomography",100,"Black & White"),
        "Lomography Berlin Kino 400" to Triple("Lomography",400,"Black & White"),
        "Lomography Purple 400"     to Triple("Lomography",400,"Color Negative (C-41)"),
        "Lomography Redscale XR 50-200" to Triple("Lomography",100,"Color Negative (C-41)"),
        "Lomography LomoChrome Metropolis" to Triple("Lomography",100,"Color Negative (C-41)"),
        "Lomography LomoChrome Purple"     to Triple("Lomography",400,"Color Negative (C-41)"),
        "Lomography LomoChrome Turquoise"  to Triple("Lomography",100,"Color Negative (C-41)"),
        // CineStill
        "CineStill 50D"          to Triple("CineStill",50,"Color Negative (C-41)"),
        "CineStill 800T"         to Triple("CineStill",800,"Color Negative (C-41)"),
        "CineStill 400D"         to Triple("CineStill",400,"Color Negative (C-41)"),
        "CineStill BwXX"         to Triple("CineStill",250,"Black & White"),
        // Rollei
        "Rollei RPX 25"          to Triple("Rollei",25,"Black & White"),
        "Rollei RPX 100"         to Triple("Rollei",100,"Black & White"),
        "Rollei RPX 400"         to Triple("Rollei",400,"Black & White"),
        "Rollei Infrared 400"    to Triple("Rollei",400,"Infrared"),
        "Rollei Ortho 25 Plus"   to Triple("Rollei",25,"Black & White"),
        "Rollei Superpan 200"    to Triple("Rollei",200,"Black & White"),
        "Rollei CR200"           to Triple("Rollei",200,"Slide (E-6)"),
        // Fomapan
        "Fomapan 100"            to Triple("Foma",100,"Black & White"),
        "Fomapan 200"            to Triple("Foma",200,"Black & White"),
        "Fomapan 400"            to Triple("Foma",400,"Black & White"),
        "Fomapan R 100"          to Triple("Foma",100,"Slide (E-6)"),
        // Kentmere
        "Kentmere Pan 100"       to Triple("Ilford",100,"Black & White"),
        "Kentmere Pan 400"       to Triple("Ilford",400,"Black & White"),
        // Bergger
        "Bergger Pancro 400"     to Triple("Bergger",400,"Black & White"),
        "Bergger Prestige 400"   to Triple("Bergger",400,"Black & White"),
        // Adox
        "Adox CMS 20 II"         to Triple("Adox",20,"Black & White"),
        "Adox HR 50"             to Triple("Adox",50,"Black & White"),
        "Adox Scala 160"         to Triple("Adox",160,"Black & White"),
        "Adox Color Mission 200" to Triple("Adox",200,"Color Negative (C-41)"),
        // Ferrania
        "Ferrania P30 Alpha 80"  to Triple("Ferrania",80,"Black & White"),
        "Film Ferrania Solaris 200" to Triple("Ferrania",200,"Color Negative (C-41)"),
        "Film Ferrania Solaris 400" to Triple("Ferrania",400,"Color Negative (C-41)"),
        // Others
        "JCH StreetPan 400"      to Triple("JCH",400,"Black & White"),
        "Kosmo Foto Mono 100"    to Triple("Kosmo Foto",100,"Black & White"),
        "Kosmo Foto Agent Shadow 400" to Triple("Kosmo Foto",400,"Black & White"),
        "Harman Phoenix 200"     to Triple("Harman",200,"Color Negative (C-41)"),
        "Harman Direct Positive 100" to Triple("Harman",100,"Black & White"),
        "AgfaPhoto Vista 200"    to Triple("AgfaPhoto",200,"Color Negative (C-41)"),
        "AgfaPhoto Vista Plus 400" to Triple("AgfaPhoto",400,"Color Negative (C-41)"),
        "Agfa APX 100"           to Triple("Agfa",100,"Black & White"),
        "Agfa APX 400"           to Triple("Agfa",400,"Black & White"),
        "Konica VX 400"          to Triple("Konica",400,"Color Negative (C-41)"),
        "Konica Centuria 200"    to Triple("Konica",200,"Color Negative (C-41)"),
        "Konica Centuria 400"    to Triple("Konica",400,"Color Negative (C-41)"),
        // Instant
        "Polaroid 600"           to Triple("Polaroid",640,"Instant"),
        "Polaroid i-Type"        to Triple("Polaroid",640,"Instant"),
        "Polaroid SX-70"         to Triple("Polaroid",160,"Instant"),
        "Fujifilm Instax Mini"   to Triple("Fujifilm",800,"Instant"),
        "Fujifilm Instax Square" to Triple("Fujifilm",800,"Instant"),
        "Fujifilm Instax Wide"   to Triple("Fujifilm",800,"Instant")
    )

}
