package de.samply.samplexchange.resources;

/**
 * Template for Mapping classes.
 *
 * <p>MII KDS is the only supported source and bbmri.de the only supported target,
 * so this template carries a single direction. See docs/adr/0001-mapping-architecture.md.
 */
public abstract class ConvertClass<T1, T2> {

    public abstract void fromMii(T2 resource);

    public abstract T1 toBbmri() throws Exception;
}
