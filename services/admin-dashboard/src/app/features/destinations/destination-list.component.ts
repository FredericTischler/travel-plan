import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AlertComponent } from '../../shared/ui/alert/alert.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { CardComponent } from '../../shared/ui/card/card.component';
import { InputComponent } from '../../shared/ui/input/input.component';
import { Destination, DestinationService } from './destination.service';
import { TRANSPORT_MODES, Transport, TransportMode, TransportService } from './transport.service';

/**
 * Destinations screen: list (GET /destinations) plus create and delete
 * actions. Every successful mutation reloads the list from the server
 * instead of mutating the local signal directly.
 *
 * No edit: travel-service exposes no PATCH/PUT on Destination.
 *
 * Also hosts the outgoing-transports sub-view for a single destination at a
 * time (toggle per row, GET /destinations/{id}/transports) plus a form to
 * create a new one-hop transport from that destination (POST
 * /destinations/{fromId}/transports). No PATCH/DELETE on Transport exists
 * server-side, so none is simulated here.
 */
@Component({
  selector: 'app-destination-list',
  imports: [FormsModule, AlertComponent, ButtonComponent, CardComponent, InputComponent],
  templateUrl: './destination-list.component.html',
})
export class DestinationListComponent implements OnInit {
  private readonly destinationService = inject(DestinationService);
  private readonly transportService = inject(TransportService);

  protected readonly destinations = signal<Destination[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  // Create form state.
  protected createName = '';
  protected createCountry = '';
  protected readonly creating = signal(false);
  protected readonly createError = signal<string | null>(null);

  // Delete state.
  protected readonly deletingDestinationId = signal<string | null>(null);
  protected readonly deleteError = signal<string | null>(null);

  // Outgoing-transports sub-view state (one destination expanded at a time).
  protected readonly transportModes = TRANSPORT_MODES;
  protected readonly expandedDestinationId = signal<string | null>(null);
  protected readonly transports = signal<Transport[]>([]);
  protected readonly transportsLoading = signal(false);
  protected readonly transportsError = signal<string | null>(null);

  // Destinations selectable as the target of a new transport: every
  // destination except the one currently expanded, so the UI itself never
  // offers a self-loop (the backend also rejects it with 400, defence in depth).
  protected readonly transportTargets = computed(() =>
    this.destinations().filter((destination) => destination.id !== this.expandedDestinationId()),
  );

  // Create-transport form state.
  protected createTransportToId = '';
  protected createTransportMode: TransportMode | '' = '';
  protected createTransportDuration: number | null = null;
  protected readonly creatingTransport = signal(false);
  protected readonly createTransportError = signal<string | null>(null);

  ngOnInit(): void {
    this.loadDestinations();
  }

  private loadDestinations(): void {
    this.loading.set(true);
    this.error.set(null);
    this.destinationService.list().subscribe({
      next: (destinations) => {
        this.destinations.set(destinations);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger la liste des destinations.');
        this.loading.set(false);
      },
    });
  }

  createDestination(): void {
    this.creating.set(true);
    this.createError.set(null);

    this.destinationService.create(this.createName, this.createCountry).subscribe({
      next: () => {
        this.creating.set(false);
        this.createName = '';
        this.createCountry = '';
        this.loadDestinations();
      },
      error: (err: HttpErrorResponse) => {
        this.creating.set(false);
        this.createError.set(
          this.extractErrorMessage(err, 'Impossible de créer cette destination.'),
        );
      },
    });
  }

  deleteDestination(destination: Destination): void {
    if (!confirm(`Supprimer la destination ${destination.name} ?`)) {
      return;
    }

    this.deletingDestinationId.set(destination.id);
    this.deleteError.set(null);

    this.destinationService.delete(destination.id).subscribe({
      next: () => {
        this.deletingDestinationId.set(null);
        this.loadDestinations();
      },
      error: (err: HttpErrorResponse) => {
        this.deletingDestinationId.set(null);
        this.deleteError.set(
          this.extractErrorMessage(err, 'Impossible de supprimer cette destination.'),
        );
      },
    });
  }

  toggleTransports(destination: Destination): void {
    if (this.expandedDestinationId() === destination.id) {
      this.expandedDestinationId.set(null);
      return;
    }

    this.expandedDestinationId.set(destination.id);
    this.resetCreateTransportForm();
    this.loadTransports(destination.id);
  }

  private loadTransports(fromId: string): void {
    this.transportsLoading.set(true);
    this.transportsError.set(null);

    this.transportService.listOutgoing(fromId).subscribe({
      next: (transports) => {
        this.transports.set(transports);
        this.transportsLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.transportsLoading.set(false);
        this.transportsError.set(
          this.extractErrorMessage(err, 'Impossible de charger les trajets de cette destination.'),
        );
      },
    });
  }

  createTransport(fromId: string): void {
    if (
      !this.createTransportToId ||
      !this.createTransportMode ||
      this.createTransportDuration === null
    ) {
      return;
    }

    this.creatingTransport.set(true);
    this.createTransportError.set(null);

    this.transportService
      .create(
        fromId,
        this.createTransportToId,
        this.createTransportMode,
        this.createTransportDuration,
      )
      .subscribe({
        next: () => {
          this.creatingTransport.set(false);
          this.resetCreateTransportForm();
          this.loadTransports(fromId);
        },
        error: (err: HttpErrorResponse) => {
          this.creatingTransport.set(false);
          this.createTransportError.set(
            this.extractErrorMessage(err, 'Impossible de créer ce trajet.'),
          );
        },
      });
  }

  private resetCreateTransportForm(): void {
    this.createTransportToId = '';
    this.createTransportMode = '';
    this.createTransportDuration = null;
    this.createTransportError.set(null);
  }

  private extractErrorMessage(err: HttpErrorResponse, fallback: string): string {
    return typeof err.error?.error === 'string' ? err.error.error : fallback;
  }
}
