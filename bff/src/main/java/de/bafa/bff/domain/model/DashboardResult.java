package de.bafa.bff.domain.model;

import java.util.List;

/**
 * Aggregated dashboard payload <em>plus</em> the execution log of how the BFF assembled it.
 *
 * <p>Symmetric to {@link AnnouncementSagaResult} on the write side: the SPA gets the data it
 * needs to render the widgets ({@code data}) and a server-authored protocol it can render
 * verbatim ({@code log}). The didactic point — visible side-by-side with the saga panel — is
 * that the workflow lives in the BFF; the SPA dispatches one read and renders both.
 *
 * <p><b>Why not just return DashboardData?</b> Without the log, a presenter has to <em>tell</em>
 * an audience that the BFF parallelised three downstream calls. With the log they <em>see</em>
 * three started/succeeded pairs interleave in real time. For a blueprint that is the central
 * teaching artefact of the read path.
 */
public record DashboardResult(DashboardData data, List<AggregationStepEntry> log) {}
