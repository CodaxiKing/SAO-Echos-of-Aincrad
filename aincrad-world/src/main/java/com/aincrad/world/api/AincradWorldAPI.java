package com.aincrad.world.api;

import com.aincrad.world.NightService;
import com.aincrad.world.SeasonsService;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/** API do AincradWorld. Métodos estáticos (dá para chamar por reflexão); também no ServicesManager. */
public final class AincradWorldAPI {

    private static final AincradWorldAPI INSTANCE = new AincradWorldAPI();
    private static SeasonsService seasons;
    private static NightService night;

    private AincradWorldAPI() {
    }

    public static void init(SeasonsService s, NightService n) {
        seasons = s;
        night = n;
    }

    public static AincradWorldAPI instance() {
        return INSTANCE;
    }

    public static boolean isEnabled() {
        return seasons != null;
    }

    /** A estação de agora: "primavera", "verao", "outono", "inverno" (ou as do calendar.yml). Null com o plugin desligado. */
    public static String getSeason() {
        return seasons == null ? null : seasons.season();
    }

    /** O dia dentro da estação, a partir de 1. */
    public static int getSeasonDay() {
        return seasons == null ? 0 : seasons.calendar().seasonDay(seasons.today());
    }

    /** Hoje é a data marcada do calendar.yml? */
    public static boolean isDate(String id) {
        return seasons != null && seasons.calendar().isDate(id, seasons.today());
    }

    /**
     * O começo (00:00, em milissegundos) do dia em que a data cai, de hoje em diante.
     * -1 se a data não existe.
     */
    public static long getDateStart(String id) {
        LocalDate d = seasons == null ? null : seasons.calendar().next(id, seasons.today());
        return d == null ? -1 : d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** O clima do andar onde o lugar fica: "limpo", "chuva", "neve" ou "nevoa". */
    public static String getWeather(Location location) {
        return seasons == null ? "limpo" : seasons.weatherAt(location).name().toLowerCase(Locale.ROOT);
    }

    /** É noite no mundo? */
    public static boolean isNight(World world) {
        return night != null && night.night(world);
    }
}
